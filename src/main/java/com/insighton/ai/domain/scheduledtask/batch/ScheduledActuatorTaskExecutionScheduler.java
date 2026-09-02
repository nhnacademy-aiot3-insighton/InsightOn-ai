package com.insighton.ai.domain.scheduledtask.batch;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.insighton.ai.adapter.client.ActuatorCommandExecutor;
import static com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary.ACTUATOR_COMMANDS;
import static com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary.COMFORT_RANGE;
import com.insighton.ai.adapter.client.dto.CallerService;
import com.insighton.ai.domain.scheduledtask.dto.ScheduledActuatorTask;
import com.insighton.ai.domain.suggestion.dto.SuggestionDraft;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 매분 폴링해 예약 시각이 된 작업만 처리한다. Rule Engine flow가 아니라 사용자가 채팅으로 직접 요청한 1회성 예약이라
 * 승인 절차 없이 바로 실행한다(ActuatorChatTool과 동일하게 채팅 요청 자체가 승인).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledActuatorTaskExecutionScheduler {

    private static final int MAX_ATTEMPTS = 3;
    // ponytail: 고정 10분 창. 센서 발행 주기가 이보다 뜸해지면 늘릴 것.
    private static final int LIVE_WINDOW_MINUTES = 10;
    private static final Set<String> COMFORT_FIELDS = Set.of("temperature", "humidity", "co2");

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final InfluxDBClient influxDBClient;
    private final HourlyTelemetryStatService hourlyTelemetryStatService;
    private final ActuatorCommandExecutor actuatorCommandExecutor;
    private final ChatClient chatClient;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Scheduled(fixedRate = 60_000)
    @SchedulerLock(name = "scheduledActuatorTaskExecution", lockAtMostFor = "PT1M", lockAtLeastFor = "PT10S")
    public void executeDueTasks() {
        Set<String> due = redisTemplate.opsForZSet()
                .rangeByScore(ScheduledActuatorTask.REDIS_KEY, 0, OffsetDateTime.now().toEpochSecond());
        if (due == null || due.isEmpty()) {
            return;
        }

        for (String raw : due) {
            redisTemplate.opsForZSet().remove(ScheduledActuatorTask.REDIS_KEY, raw);
            ScheduledActuatorTask task = jsonMapper.readValue(raw, ScheduledActuatorTask.class);
            try {
                doExecute(task);
            } catch (Exception e) {
                log.error("예약 작업 실행 실패 - taskId:{}, attempt:{}", task.taskId(), task.attemptCount(), e);
                retryOrGiveUp(task);
            }
        }
    }

    private void retryOrGiveUp(ScheduledActuatorTask task) {
        if (task.attemptCount() + 1 >= MAX_ATTEMPTS) {
            log.error("예약 작업 최대 재시도 초과, 포기 - taskId:{}", task.taskId());
            return;
        }
        ScheduledActuatorTask retried = new ScheduledActuatorTask(
                task.taskId(), task.groupId(), task.locationId(), task.purposeText(), task.attemptCount() + 1);
        redisTemplate.opsForZSet().add(ScheduledActuatorTask.REDIS_KEY,
                jsonMapper.writeValueAsString(retried), OffsetDateTime.now().plusMinutes(1).toEpochSecond());
    }

    private void doExecute(ScheduledActuatorTask task) {
        OffsetDateTime lastCompletedHour = OffsetDateTime.now(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.HOURS).minusHours(1);
        PeriodTelemetrySummary hourly = hourlyTelemetryStatService.summarizePeriod(
                task.locationId(), lastCompletedHour, lastCompletedHour);
        Map<String, Double> live = queryLiveSensorValues(task.locationId());

        SuggestionDraft draft = chatClient.prompt().user(buildPrompt(task, hourly, live))
                .call().entity(SuggestionDraft.class);

        if (!draft.actionNeeded() || draft.actions().isEmpty()) {
            log.info("예약 준비 - 조치 불필요, taskId:{}", task.taskId());
            return;
        }

        actuatorCommandExecutor.execute(task.groupId(), task.locationId(), draft.actions(), CallerService.AI_SYSTEM);
        log.info("예약 준비 실행 완료 - taskId:{}, locationId:{}, 명령 수:{}",
                task.taskId(), task.locationId(), draft.actions().size());
    }

    private Map<String, Double> queryLiveSensorValues(Long locationId) {
        String fieldFilter = COMFORT_FIELDS.stream()
                .map(f -> "r._field == \"" + f + "\"")
                .collect(Collectors.joining(" or "));
        String flux = """
                from(bucket: "%s")
                |> range(start: -%dm)
                |> filter(fn: (r) => r._measurement == "sensor_data" and r.location_id == "%d" and (%s))
                |> group(columns: ["_field"])
                |> mean()
                """.formatted(bucket, LIVE_WINDOW_MINUTES, locationId, fieldFilter);

        Map<String, Double> result = new HashMap<>();
        for (FluxTable table : influxDBClient.getQueryApi().query(flux)) {
            for (FluxRecord record : table.getRecords()) {
                result.put(record.getField(), ((Number) record.getValue()).doubleValue());
            }
        }
        return result;
    }

    private String buildPrompt(ScheduledActuatorTask task, PeriodTelemetrySummary hourly, Map<String, Double> live) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 스마트 오피스의 실내 환경을 쾌적하게 준비하는 AI입니다.\n");
        sb.append("사용자가 곧(목적: ").append(task.purposeText() != null ? task.purposeText() : "미상")
                .append(") 이 공간을 쓸 예정이라 미리 쾌적하게 준비해달라고 요청했습니다. 필요하면 액추에이터를 조작하세요.\n\n");

        sb.append("## 1시간 전 확정 집계\n");
        hourly.metricsAvg().forEach((metric, value) ->
                sb.append("- ").append(metric).append(": ").append(value).append("\n"));

        sb.append("\n## 지금(최근 ").append(LIVE_WINDOW_MINUTES).append("분 평균)\n");
        live.forEach((metric, value) -> sb.append("- ").append(metric).append(": ").append(value).append("\n"));

        sb.append("\n## 쾌적 기준값\n");
        COMFORT_RANGE.forEach((metric, range) ->
                sb.append("- ").append(metric).append(": ").append(range[0]).append("~").append(range[1]).append("\n"));

        sb.append("\n## 조작 가능한 명령과 허용값\n");
        ACTUATOR_COMMANDS.forEach((type, commands) -> {
            sb.append("- ").append(type).append("\n");
            commands.forEach((command, allowedValues) ->
                    sb.append("  - ").append(command).append(": ").append(allowedValues).append("\n"));
        });

        sb.append("\n---\n");
        sb.append("actions 배열의 각 항목(actuatorType/command/commandValue)은 반드시 위 목록에 있는 조합만 쓰세요. ")
                .append("지금 상태가 이미 쾌적 기준값 안이면 actionNeeded=false로 하고 나머지는 비우세요.");
        return sb.toString();
    }
}
