package com.insighton.ai.domain.suggestion.batch;

import com.insighton.ai.adapter.client.ActuatorCommandExecutor;
import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.ActionPayload;
import com.insighton.ai.adapter.client.dto.AutoControlMode;
import com.insighton.ai.adapter.client.dto.CallerService;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.adapter.client.dto.WeatherResponse;
import com.insighton.ai.domain.suggestion.dto.RejectionPattern;
import com.insighton.ai.domain.suggestion.dto.SuggestionDraft;
import com.insighton.ai.domain.suggestion.dto.SuggestionLogCreateRequest;
import com.insighton.ai.domain.suggestion.event.AiSuggestionActionEvent;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 업무시간(평일 9~17시) 매 정각 5분 뒤, 방금 집계된 hourly_telemetry_stats를 기준으로 location별 조치 필요 여부를 LLM에게 판단시켜 필요한 경우에만
 * suggestion_logs에 제안을 남긴다. Rule Engine의 AI_SUGGESTION_ACTION 이벤트가 오면 정각 스케줄과 무관하게 이벤트 전용 경로로 즉시 한 번 더 판단. location의
 * auto_control_mode가 AI_DIRECT면 생성과 동시에 Core 제어 API를 호출해 즉시 실행하고, SUGGESTION이면 대기 상태로 저장해 사용자 수락을 기다림.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SuggestionGenerationScheduler {

    private final ActuatorCommandExecutor actuatorCommandExecutor;

    private static final Map<String, double[]> COMFORT_RANGE = Map.of(
            "temperature", new double[]{20.0, 26.0},
            "co2", new double[]{0.0, 1000.0},
            "humidity", new double[]{40.0, 60.0}
    );

    // Core com.insighton.core.domain.actuators.policy의 CommandType/CommandValueRule 확정값과 동일하게 유지할 것
    private static final Map<String, Map<String, String>> ACTUATOR_COMMANDS = Map.of(
            "AIRCON", Map.of(
                    "POWER_STATUS", "ON, OFF",
                    "OPERATION_MODE", "COOL, DRY, FAN, AUTO",
                    "SET_TEMPERATURE", "18~30 사이 숫자"
            ),
            "AIR_PURIFIER", Map.of(
                    "POWER_STATUS", "ON, OFF",
                    "OPERATION_MODE", "AUTO, SLEEP, TURBO"
            ),
            "VENTILATION_FAN", Map.of(
                    "POWER_STATUS", "ON, OFF",
                    "OPERATION_MODE", "LOW, MID, HIGH"
            )
    );

    private final HourlyTelemetryStatService hourlyTelemetryStatService;
    private final CoreClient coreClient;
    private final SuggestionLogService suggestionLogService;
    private final ChatClient chatClient;
    private final JsonMapper jsonMapper;

    /**
     * 업무시간(평일 9~17시) 매 정각 5분 뒤 실행. 정각 통계 집계 배치(0분에 실행)와 Core 날씨 캐시 갱신(정각으로 가정)이 끝난 뒤 도는 걸 보장하기 위해 지연.
     */
    @Scheduled(cron = "0 5 9-17 * * MON-FRI", zone = "Asia/Seoul")
    @SchedulerLock(name = "suggestionGeneration", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void generateSuggestions() {
        log.info("AI제안 스케줄러 작동");
        OffsetDateTime currentHour = OffsetDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.HOURS)
                .minusHours(1);

        List<Long> locationIds = hourlyTelemetryStatService.findDistinctLocationIds(currentHour, currentHour);

        for (Long locationId : locationIds) {
            try {
                generateOneSuggestion(locationId, currentHour);
            } catch (Exception e) {
                log.error("제안 생성 실패 - locationId:{}", locationId, e);
            }
        }
        log.info("제안 생성 배치 완료 - logHour:{}, 대상 location 수:{}", currentHour, locationIds.size());
    }

    /**
     * location 하나에 대해 이번 시간/직전 시간 데이터와 날씨를 모아 LLM에게 조치 필요 여부를 판단시키고, 필요하면 auto_control_mode에 따라 즉시 실행하거나 사용자 수락 대기 상태로
     * 저장한다.
     *
     * @param locationId  대상 위치 ID
     * @param currentHour 방금 집계된 시간(hourly_telemetry_stats의 logHour)
     */
    void generateOneSuggestion(Long locationId, OffsetDateTime currentHour) {
        log.info("locationId: {} , AI제안 시작", locationId);
        PeriodTelemetrySummary current = hourlyTelemetryStatService.summarizePeriod(locationId, currentHour,
                currentHour);
        if (current.metricsAvg().isEmpty()) {
            return;
        }

        OffsetDateTime previousHour = currentHour.minusHours(1);
        PeriodTelemetrySummary previous = hourlyTelemetryStatService.summarizePeriod(locationId, previousHour,
                previousHour);

        LocationResponse location = coreClient.getLocation(locationId);
        WeatherResponse weather = tryGetWeather(location.groupId());

        String prompt = buildSuggestionPrompt(current, previous, weather, locationId);
        SuggestionDraft draft = chatClient.prompt().user(prompt).call().entity(SuggestionDraft.class);

        applyDraft(locationId, location, draft, "정기");
    }

    /**
     * Rule Engine의 AI_SUGGESTION_ACTION 이벤트로 트리거되는 즉시 제안 생성. 정각 스케줄과 무관하게 실행되며, 이벤트가 담고 있는 실시간 감지값(metricKey/value)을
     * 프롬프트 최상단에 명시해 즉시성을 반영한다. 최근 집계 데이터가 아직 없어도(직전 시간 집계가 비어 있어도) 이벤트 자체가 판단 근거가 되므로 생성을 건너뛰지 않는다.
     *
     * @param event Rule Engine이 전달한 제안 트리거 이벤트
     */
    public void generateEventTriggeredSuggestion(AiSuggestionActionEvent event) {
        OffsetDateTime lastCompletedHour = OffsetDateTime.now(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.HOURS).minusHours(1);

        PeriodTelemetrySummary current = hourlyTelemetryStatService.summarizePeriod(
                event.locationId(), lastCompletedHour, lastCompletedHour);

        LocationResponse location = coreClient.getLocation(event.locationId());
        WeatherResponse weather = tryGetWeather(location.groupId());

        String prompt = buildEventTriggerPrompt(event, current, weather);
        SuggestionDraft draft = chatClient.prompt().user(prompt).call().entity(SuggestionDraft.class);

        applyDraft(event.locationId(), location, draft, "이벤트 기반");
    }

    /**
     * LLM 판단 결과를 실제 제안 저장/실행으로 반영한다. actionNeeded가 false면 아무것도 하지 않고, true면 auto_control_mode에 따라 즉시 실행하거나 사용자 수락 대기
     * 상태로 저장한다. 정기/이벤트 두 경로가 LLM 호출 이후 동일하게 수행하는 로직이라 공통으로 뺐다. 창문 개방처럼 액추에이터가 아닌 조언만 있는 경우(actuatorType 없음)는 자동 실행 대상이
     * 아니므로 suggestionText만으로 대기 상태 제안을 남긴다.
     *
     * @param locationId  대상 위치 ID
     * @param location    Core에서 조회한 위치 정보(groupId, autoControlMode 판단용)
     * @param draft       LLM이 생성한 제안 초안
     * @param sourceLabel 로그 구분용 라벨(정기/이벤트 기반)
     */
    private void applyDraft(Long locationId, LocationResponse location, SuggestionDraft draft, String sourceLabel) {
        if (!draft.actionNeeded()) {
            log.info("{} 제안 불필요 - locationId:{}", sourceLabel, locationId);
            return;
        }

        ActionPayload actionPayload = new ActionPayload(locationId, draft.actions());
        String actionPayloadJson = toJson(actionPayload);

        boolean autoExecute = !actionPayload.actions().isEmpty()
                && location.autoControlMode() == AutoControlMode.AI_DIRECT;

        suggestionLogService.create(new SuggestionLogCreateRequest(
                location.groupId(), locationId, draft.title(), draft.suggestionText(),
                actionPayloadJson, autoExecute ? true : null
        ));

        if (autoExecute) {
            actuatorCommandExecutor.execute(location.groupId(), actionPayload.locationId(), actionPayload.actions(),
                    CallerService.AI_SYSTEM);
        }

        log.info("{} 제안 생성 - locationId:{}, autoExecute:{}, 명령 수:{}", sourceLabel, locationId, autoExecute,
                actionPayload.actions().size());
    }

    /**
     * Core 날씨 조회가 실패해도(일시 오류 등) 배치 전체가 죽지 않도록 감싸서, 실패 시 null을 반환. 프롬프트 조립 단계에서 null이면 날씨 섹션 자체를 생략.
     */
    private WeatherResponse tryGetWeather(Long groupId) {
        try {
            return coreClient.getWeather(groupId);
        } catch (Exception e) {
            log.warn("날씨 조회 실패, 날씨 정보 없이 진행 - groupId:{}", groupId);
            return null;
        }
    }

    /**
     * 재집계된 데이터를 LLM 프롬프트 텍스트로 조립한다. 정기 스케줄 전용으로, 직전 시간 대비 변화를 추가로 보여준 뒤 공통 컨텍스트를 이어붙인다.
     */
    private String buildSuggestionPrompt(PeriodTelemetrySummary current, PeriodTelemetrySummary previous,
                                         WeatherResponse weather, Long locationId) {
        StringBuilder sb = new StringBuilder();

        sb.append("당신은 스마트 오피스의 실내 환경을 모니터링하고 액추에이터 조작을 제안하는 AI입니다.\n");
        sb.append("아래는 방금 집계된 이번 시간(").append(current.from()).append(") 실내 환경 데이터입니다. ")
                .append("상황을 보고 조치가 필요한지 판단하세요.\n\n");

        if (previous != null && !previous.metricsAvg().isEmpty()) {
            sb.append("## 직전 시간 대비\n");
            previous.metricsAvg().forEach((metric, prevValue) -> {
                Double currValue = current.metricsAvg().get(metric);
                if (currValue != null) {
                    sb.append("- ").append(metric).append(": ").append(prevValue).append(" → ").append(currValue)
                            .append("\n");
                }
            });
            sb.append("\n");
        }

        sb.append(buildCommonContext(current, weather, locationId));
        return sb.toString();
    }

    /**
     * Rule Engine 이벤트 전용 프롬프트. 이벤트가 담고 있는 실시간 감지값(metricKey/value)을 최상단에 명시해 즉시 반응해야 하는 상황임을 알린 뒤 공통 컨텍스트를 이어붙임.
     */
    private String buildEventTriggerPrompt(AiSuggestionActionEvent event, PeriodTelemetrySummary current,
                                           WeatherResponse weather) {
        StringBuilder sb = new StringBuilder();

        sb.append("당신은 스마트 오피스의 실내 환경을 모니터링하고 액추에이터 조작을 제안하는 AI입니다.\n");
        sb.append("방금(").append(event.timestamp()).append(") 이상 감지 이벤트가 발생했습니다: ")
                .append(event.metricKey()).append(" = ").append(event.value()).append("\n");
        sb.append("아래는 최근 집계된 실내 환경 데이터입니다. 이 상황을 보고 조치가 필요한지 판단하세요.\n\n");

        sb.append(buildCommonContext(current, weather, event.locationId()));
        return sb.toString();
    }

    /**
     * 정기/이벤트 두 프롬프트가 공통으로 쓰는 컨텍스트(쾌적 기준값, 실내 환경, 액추에이터 가동, 날씨, 조작 가능 명령, 거절 패턴, 출력 지침)를 조립한다. 쾌적 기준값/조작 가능 명령 목록은
     * 실제 존재하는 것만 명시해 회사마다 다른 센서/액추에이터 구성에 그대로 대응.
     */
    private String buildCommonContext(PeriodTelemetrySummary current, WeatherResponse weather, Long locationId) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 쾌적 기준값 (알려진 지표만)\n");
        current.metricsAvg().keySet().forEach(metric -> {
            double[] range = COMFORT_RANGE.get(metric);
            if (range != null) {
                sb.append("- ").append(metric).append(": ").append(range[0]).append("~").append(range[1]).append("\n");
            }
        });

        sb.append("\n## 실내 환경\n");
        current.metricsAvg().forEach((metric, value) ->
                sb.append("- ").append(metric).append(": ").append(value).append("\n"));

        sb.append("\n## 액추에이터 가동\n");
        current.actuatorOnMinutes().forEach((type, minutes) ->
                sb.append("- ").append(type).append(": ").append(minutes).append("분\n"));

        if (weather != null) {
            sb.append("\n## 실외 환경 (현재)\n");
            sb.append("- 기온: ").append(orNone(weather.temperature())).append("°C\n");
            sb.append("- 오늘 예상 기온 범위: ").append(orNone(weather.minTemp())).append("°C ~ ")
                    .append(orNone(weather.maxTemp())).append("°C\n");
            sb.append("- 하늘상태: ").append(orNone(weather.skyStatus())).append("\n");
            sb.append("- 강수: ").append(orNone(weather.precipitationType())).append("\n");
            sb.append("- 미세먼지: ").append(orNone(weather.dustGrade())).append("\n");

            sb.append("\n## 실외 환경 (1시간 후 예보)\n");
            sb.append("- 기온: ").append(orNone(weather.forecastTemperature())).append("°C\n");
            sb.append("- 하늘상태: ").append(orNone(weather.forecastSkyStatus())).append("\n");
            sb.append("- 강수: ").append(orNone(weather.forecastPrecipitationType())).append("\n");
            sb.append("- 습도: ").append(orNone(weather.forecastHumidity())).append("%\n");
        }

        sb.append("\n## 조작 가능한 명령과 허용값 (이 위치에 실제로 있는 액추에이터만, 이 목록 안에서만 선택)\n");
        Set<String> presentActuatorTypes = current.actuatorOnMinutes().keySet();
        if (presentActuatorTypes.isEmpty()) {
            sb.append("- 이 위치에는 조작 가능한 액추에이터가 없습니다. actions는 항상 빈 배열로 두세요.\n");
        } else {
            ACTUATOR_COMMANDS.forEach((type, commands) -> {
                if (!presentActuatorTypes.contains(type)) {
                    return;
                }
                sb.append("- ").append(type).append("\n");
                commands.forEach((command, allowedValues) ->
                        sb.append("  - ").append(command).append(": ").append(allowedValues).append("\n"));
            });
        }

        List<RejectionPattern> rejectionPatterns = suggestionLogService.findRejectionPatterns(locationId);
        if (!rejectionPatterns.isEmpty()) {
            sb.append("\n## 참고 - 최근 자주 거절된 조작 (참고용 신호이며, 현재 상황이 다르면 무시해도 됨)\n");
            rejectionPatterns.forEach(p ->
                    sb.append("- ").append(p.actuatorType()).append(" ").append(p.command()).append("=")
                            .append(p.commandValue()).append(": 최근 ").append(p.totalCount()).append("건 중 ")
                            .append(p.rejectedCount()).append("건 거절됨\n"));
        }

        sb.append("\n---\n");
        sb.append("쾌적 기준값을 벗어났거나 벗어날 조짐이 보이면 actionNeeded=true로 하세요. ")
                .append("actions 배열의 각 항목(actuatorType/command/commandValue)은 반드시 위 목록에 있는 조합만 쓰세요. ")
                .append("필요하면 여러 액추에이터를 동시에 조작하도록 actions에 여러 개를 담아도 됩니다(예: 에어컨 끄고 환풍기 켜기).\n");
        sb.append("실외 기온이 쾌적하고 미세먼지가 좋음/보통이며, 현재는 물론 1시간 후 예보에도 강수가 없다면, ")
                .append("액추에이터를 끄고 자연환기(창문 개방)를 제안하는 것도 고려하세요. ")
                .append("1시간 후 강수가 예보돼 있다면 창문 개방은 제안하지 마세요. ")
                .append("창문 개방처럼 액추에이터가 아닌 조언은 suggestionText에만 포함하고, ")
                .append("actions에는 실제로 조작 가능한 액추에이터 명령만 담으세요(없으면 빈 배열).\n");
        sb.append("특별히 조치할 게 없으면 actionNeeded=false로 하고 나머지 필드는 비우세요.");

        return sb.toString();
    }

    private String orNone(Object value) {
        return value != null ? String.valueOf(value) : "정보없음";
    }

    private String toJson(ActionPayload actionPayload) {
        try {
            return jsonMapper.writeValueAsString(actionPayload);
        } catch (Exception e) {
            throw new IllegalStateException("액추에이터 명령 JSON 직렬화 실패", e);
        }
    }
}
