package com.insighton.ai.domain.report.batch;

import static com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary.BUSINESS_HOUR_END;
import static com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary.BUSINESS_HOUR_START;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.FlowDraftRequester;
import com.insighton.ai.adapter.client.RuleEngineClient;
import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.ActuatorCommandSummary;
import com.insighton.ai.adapter.client.dto.ActuatorRunLogResponse;
import com.insighton.ai.adapter.client.dto.FlowSummaryResponse;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.adapter.client.dto.WeatherResponse;
import com.insighton.ai.domain.enginealert.dto.EngineAlertSummary;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
import com.insighton.ai.domain.flow.FlowActionPromptBuilder;
import com.insighton.ai.domain.report.dto.FlowActionDecision;
import com.insighton.ai.domain.report.dto.FlowActionDecisions;
import com.insighton.ai.domain.report.dto.GroupComparisonSummary;
import com.insighton.ai.domain.report.dto.MetricDiff;
import com.insighton.ai.domain.report.dto.ReportCreateRequest;
import com.insighton.ai.domain.report.entity.Report;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.service.ReportService;
import com.insighton.ai.domain.suggestion.dto.SuggestionSummary;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import com.insighton.ai.domain.telemetrystats.dto.HourlyPeakPattern;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매주 월요일/매월 1일 정각, 직전 기간의 hourly_telemetry_stats/engine_alerts/suggestion_logs를 재집계해 LLM으로 진단 리포트를 생성하고 reports에 저장한다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReportGenerationScheduler {

    private static final Map<String, double[]> COMFORT_RANGE = Map.of(
            "temperature", new double[]{20.0, 26.0},
            "co2", new double[]{0.0, 1000.0},
            "humidity", new double[]{40.0, 60.0}
    );

    private static final double GROUP_COMPARISON_THRESHOLD_PERCENT = 15.0;

    private final HourlyTelemetryStatService hourlyTelemetryStatService;
    private final EngineAlertService engineAlertService;
    private final SuggestionLogService suggestionLogService;
    private final CoreClient coreClient;
    private final ReportService reportService;
    private final ChatClient chatClient;
    private final FlowDraftRequester flowDraftRequester;
    private final FlowActionPromptBuilder flowActionPromptBuilder;
    private final RuleEngineClient ruleEngineClient;

    /**
     * 매주 월요일 00:00 실행. 직전 월~일(7일)을 이번 기간, 그 전 7일을 비교 기준(지난 기간)
     */
    // TEST-ONLY: 하루 한 번(매일 00:00) 실행되도록 임시 변경 — 검증 끝나면 반드시 "0 0 0 * * MON"으로 되돌릴 것
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "weeklyReportGeneration", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void generateWeeklyReports() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS);

        generateReports(ReportType.WEEKLY, now.minusWeeks(1), now.minusHours(1),
                now.minusWeeks(2), now.minusWeeks(1).minusHours(1));
    }

    /**
     * 매월 1일 00:00 실행. 직전 달 1일~말일을 이번 기간, 그 전달을 비교 기준(지난 기간)
     */
    // TEST-ONLY: 30분마다 실행되도록 임시 변경 — 검증 끝나면 반드시 "0 0 0 1 * *"로 되돌릴 것
    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "monthlyReportGeneration", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void generateMonthlyReports() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS);
        generateReports(ReportType.MONTHLY,
                now.minusMonths(1), now.minusHours(1),
                now.minusMonths(2), now.minusMonths(1).minusHours(1));
    }

    /**
     * hourly_telemetry_stats에 이번 기간 데이터가 존재하는 location 전체를 순회하며 리포트를 생성한다. location 하나가 실패(Core 응답 실패, LLM 호출 실패 등)해도
     * 나머지 location 처리는 계속 진행
     *
     * @param reportType      WEEKLY 또는 MONTHLY
     * @param periodStart     이번 기간 집계 시작(포함)
     * @param periodEnd       이번 기간 집계 종료(포함)
     * @param prevPeriodStart 지난 기간 집계 시작(포함)
     * @param prevPeriodEnd   지난 기간 집계 종료(포함)
     */
    private void generateReports(ReportType reportType,
                                 OffsetDateTime periodStart, OffsetDateTime periodEnd,
                                 OffsetDateTime prevPeriodStart, OffsetDateTime prevPeriodEnd) {

        List<Long> locationIds = hourlyTelemetryStatService.findDistinctLocationIds(periodStart, periodEnd);

        for (Long locationId : locationIds) {
            try {
                generateOneReport(reportType, locationId, periodStart, periodEnd, prevPeriodStart, prevPeriodEnd);
            } catch (Exception e) {
                log.error("리포트 생성 실패 - reportType:{}, locationId:{}", reportType, locationId, e);
            }
        }
        log.info("리포트 생성 배치 완료 - reportType:{}, 대상 location 수:{}", reportType, locationIds.size());
    }

    /**
     * location 하나에 대해 데이터 재집계 → LLM 프롬프트 조립 → 호출 → reports 저장
     *
     * @param reportType      WEEKLY 또는 MONTHLY
     * @param locationId      대상 위치 ID
     * @param periodStart     이번 기간 집계 시작(포함)
     * @param periodEnd       이번 기간 집계 종료(포함)
     * @param prevPeriodStart 지난 기간 집계 시작(포함)
     * @param prevPeriodEnd   지난 기간 집계 종료(포함)
     */
    void generateOneReport(ReportType reportType, Long locationId,
                           OffsetDateTime periodStart, OffsetDateTime periodEnd,
                           OffsetDateTime prevPeriodStart, OffsetDateTime prevPeriodEnd) {

        LocationResponse location = coreClient.getLocation(locationId);

        PeriodTelemetrySummary current = hourlyTelemetryStatService.summarizePeriod(locationId, periodStart, periodEnd);

        PeriodTelemetrySummary previous = hourlyTelemetryStatService.summarizePeriod(locationId, prevPeriodStart,
                prevPeriodEnd);

        EngineAlertSummary alerts = engineAlertService.summarizePeriod(locationId, periodStart, periodEnd);

        SuggestionSummary suggestions = suggestionLogService.summarizePeriod(locationId, periodStart, periodEnd);

        ActuatorCommandSummary actuatorCommands = summarizeActuatorCommands(locationId, periodStart, periodEnd);

        GroupComparisonSummary groupComparison = buildGroupComparison(locationId, location.groupId(), current,
                periodStart, periodEnd);

        List<HourlyPeakPattern> peakPatterns = reportType == ReportType.MONTHLY
                ? hourlyTelemetryStatService.extractPeakPatterns(current)
                : List.of();

        List<FlowSummaryResponse> aiFlows = tryFindAiFlows(location.groupId(), locationId);
        WeatherResponse weather = hasAiFlows(aiFlows) ? tryGetWeather(location.groupId()) : null;

        String prompt = buildPrompt(reportType, current, previous, alerts, suggestions, actuatorCommands,
                groupComparison, peakPatterns, aiFlows, weather);

        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        String title = buildTitle(periodStart, location.locationName(), reportType);

        Report savedReport = reportService.createReport(new ReportCreateRequest(
                location.groupId(), locationId, title, reportType, content
        ));

        requestFlowDrafts(location.groupId(), locationId, savedReport.getReportId(), title, current, peakPatterns);

        log.info("리포트 생성 - reportType:{}, locationId:{}", reportType, locationId);
    }

    /**
     * 업무시간(BUSINESS_HOUR_START~END) 내 피크만 골라 LLM에게 "예방적 자동화가 적절한지, 적절하다면 어떤 액추에이터를 어떻게 조작할지"를 직접 판단시킨 뒤, 그 판단대로만 Rule
     * Engine에 flow 초안 생성을 요청한다 - 어떤 조작을 할지를 고정된 매핑표로 미리 정해두면 AI 판단이 아니라 규칙표 조회가 되어버리므로, 매번 상황(패턴 크기, 위치에 실제 있는 액추에이터)을
     * 보고 LLM이 결정하게 한다. 업무시간 밖 피크(예: 새벽)는 사람이 없어 자동화해도 의미가 없어 대상에서 제외한다 - 그 시간대 패턴을 리포트에서 서술하는 건 여전히 유효한 진단 정보라
     * buildPrompt() 쪽은 원본 peakPatterns를 그대로 쓴다. 이 위치에 액추에이터가 하나도 없으면 LLM 호출 자체를 건너뛴다. 실패는 {@link FlowDraftRequester}가
     * 자체적으로 흡수하므로 여기선 별도 try-catch가 필요 없다.
     */
    private void requestFlowDrafts(Long groupId, Long locationId, Long reportId, String reportTitle,
                                   PeriodTelemetrySummary current, List<HourlyPeakPattern> peakPatterns) {
        // 업무시간(SuggestionGenerationScheduler가 쓰는 9~17시와 동일 기준) 밖의 피크는 자동화 대상에서 제외한다 -
        // 새벽에 튀는 패턴을 그 시간에 미리 대응해봤자 사람이 없어서 의미가 없다. 리포트 서술(buildPrompt)에는
        // peakPatterns 원본을 그대로 쓰므로, 진단 정보로서의 가치는 그대로 유지된다 - 자동화 생성만 막는다.
        List<HourlyPeakPattern> businessHourPatterns = peakPatterns.stream()
                .filter(pattern -> pattern.peakHour() >= BUSINESS_HOUR_START && pattern.peakHour() <= BUSINESS_HOUR_END)
                .toList();

        Set<String> presentActuatorTypes = current.actuatorOnMinutes().keySet();
        if (businessHourPatterns.isEmpty() || presentActuatorTypes.isEmpty()) {
            log.info("flow 자동화 판단 스킵 - locationId:{}, 업무시간 내 피크 수:{}, 보유 액추에이터:{}",
                    locationId, businessHourPatterns.size(), presentActuatorTypes);
            return;
        }

        String prompt = flowActionPromptBuilder.build(businessHourPatterns, presentActuatorTypes);
        FlowActionDecisions result = chatClient.prompt().user(prompt).call().entity(FlowActionDecisions.class);
        log.info("flow 자동화 LLM 판단 결과 - locationId:{}, 판단 수:{}", locationId, result.decisions().size());

        Map<String, HourlyPeakPattern> patternsByMetric = businessHourPatterns.stream()
                .collect(Collectors.toMap(HourlyPeakPattern::metric, Function.identity(), (a, b) -> a));

        for (FlowActionDecision decision : result.decisions()) {
            HourlyPeakPattern pattern = patternsByMetric.get(decision.metric());
            boolean actuatorPresent = decision.actuatorType() != null
                    && presentActuatorTypes.contains(decision.actuatorType().name());
            if (!decision.automationRecommended() || pattern == null || !actuatorPresent) {
                log.info("flow 자동화 대상 제외 - locationId:{}, metric:{}, automationRecommended:{}, "
                                + "actuatorType:{}, patternMatch:{}, actuatorPresent:{}",
                        locationId, decision.metric(), decision.automationRecommended(),
                        decision.actuatorType(), pattern != null, actuatorPresent);
                continue;
            }
            ActuatorAction action = new ActuatorAction(decision.actuatorType(), decision.command(),
                    decision.commandValue());
            flowDraftRequester.requestDraft(groupId, locationId, reportTitle + " #" + reportId, pattern, action);
        }
    }

    /**
     * 리포트의 "관리 중인 자동화" 섹션용으로 그 위치의 flow 목록을 조회하고, AI가 만든 것("[AI] " 접두어)만 남긴다. Rule Engine 미응답/장애가 리포트 생성 자체를 막으면 안 되므로
     * 실패 시 빈 목록으로 안전하게 진행한다.
     */
    private List<FlowSummaryResponse> tryFindAiFlows(Long groupId, Long locationId) {
        try {
            return ruleEngineClient.findFlows(groupId, locationId).stream()
                    .filter(flow -> flow.name().startsWith("[AI] "))
                    .toList();
        } catch (Exception e) {
            log.warn("관리 중인 자동화 목록 조회 실패, 빈 목록으로 진행 - locationId:{}", locationId, e);
            return List.of();
        }
    }

    private boolean hasAiFlows(List<FlowSummaryResponse> aiFlows) {
        return !aiFlows.isEmpty();
    }

    /**
     * "관리 중인 자동화" 섹션에서 계절이 안 맞아 재검토가 필요해 보이는 자동화를 LLM이 짚어낼 수 있게 현재 실외 날씨를 조회한다. AI 플로우가 하나도 없으면 애초에 호출하지 않는다(hasAiFlows
     * 가드, generateOneReport 참고). Core 미응답이 리포트 생성 자체를 막으면 안 되므로 실패 시 null로 안전하게 진행(SuggestionGenerationScheduler와 동일
     * 패턴).
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
     * Core actuator_run_logs 원본을 조회해 설정 온도 변경 이력/조작 주체 비율로 가공한다. 원본 로그가 없으면(이력 없음) 전부 0/빈 값으로 채움.
     *
     * @param locationId  대상 위치 ID
     * @param periodStart 집계 시작(포함)
     * @param periodEnd   집계 종료(포함)
     * @return 액추에이터 조작 이력 요약
     */
    private ActuatorCommandSummary summarizeActuatorCommands(Long locationId, OffsetDateTime periodStart,
                                                             OffsetDateTime periodEnd) {
        List<ActuatorRunLogResponse> logs = coreClient.getActuatorRunLogs(List.of(locationId), periodStart, periodEnd);

        List<Double> setTemperatures = logs.stream()
                .filter(logEntry -> "SET_TEMPERATURE".equals(logEntry.commandType()))
                .map(logEntry -> Double.parseDouble(logEntry.commandValue()))
                .toList();

        double avgSetTemperature = setTemperatures.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        Map<String, Long> executedByCount = new HashMap<>();
        logs.forEach(logEntry -> executedByCount.merge(logEntry.executedByType(), 1L, Long::sum));

        Map<String, Long> executedByRatio = new HashMap<>();
        if (!logs.isEmpty()) {
            executedByCount.forEach((executedByType, count) ->
                    executedByRatio.put(executedByType, Math.round(count * 100.0 / logs.size())));
        }

        return new ActuatorCommandSummary(avgSetTemperature, setTemperatures.size(), executedByRatio);
    }

    /**
     * 같은 그룹 내 다른 위치들과 이번 기간 실내 환경/액추에이터 가동시간을 비교한다. 그룹 평균 대비 차이가 ±{@value GROUP_COMPARISON_THRESHOLD_PERCENT}% 이상인 항목만
     * 남긴다 - 사소한 차이까지 리포트에 억지로 언급하지 않기 위함. 그룹에 데이터가 있는 다른 위치가 하나도 없으면(1인 그룹, 신규 그룹 등)
     * {@link GroupComparisonSummary#empty()}를 반환해 리포트에서 이 섹션 자체를 생략시킨다.
     *
     * <p>ponytail: 위치가 N개인 그룹은 리포트 N개를 만들며 매번 다른 위치 전체를 재집계해 배치당 O(N^2) 조회가 발생한다. 주1회/월1회 배치라 지금 규모(그룹당
     * 수십 곳 이내)에선 무해하지만, 그룹이 커지면 배치 단위로 그룹별 재집계 결과를 캐싱하는 걸로 업그레이드할 것.
     */
    private GroupComparisonSummary buildGroupComparison(Long locationId, Long groupId, PeriodTelemetrySummary current,
                                                        OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        List<PeriodTelemetrySummary> others = coreClient.getLocationsByGroup(groupId).stream()
                .map(LocationResponse::locationId)
                .filter(otherId -> !otherId.equals(locationId))
                .map(otherId -> hourlyTelemetryStatService.summarizePeriod(otherId, periodStart, periodEnd))
                // metricsAvg만 보고 걸렀었는데, 그러면 센서 없이 액추에이터만 있는 위치가 액추에이터 비교에서조차
                // 통째로 빠짐 - 둘 중 하나라도 있으면 남긴다(어느 쪽이 실제로 비교 가능한지는 diffAgainstGroup이
                // 지표/액추에이터 키 단위로 이미 독립적으로 걸러낸다).
                .filter(summary -> !summary.metricsAvg().isEmpty() || !summary.actuatorOnMinutes().isEmpty())
                .toList();

        if (others.isEmpty()) {
            return GroupComparisonSummary.empty();
        }

        Map<String, MetricDiff> metricDiffs = diffAgainstGroup(current.metricsAvg(), others,
                PeriodTelemetrySummary::metricsAvg);
        Map<String, MetricDiff> actuatorDiffs = diffAgainstGroup(current.actuatorOnMinutes(), others,
                PeriodTelemetrySummary::actuatorOnMinutes);

        return new GroupComparisonSummary(others.size(), metricDiffs, actuatorDiffs);
    }

    /**
     * currentValues의 각 항목을 others 중 같은 키를 가진 위치들만의 평균과 비교한다. 위치마다 있는 액추에이터 종류가 다를 수 있어("이 방은 에어컨만, 저 방은 +공기청정기"), 키가 아예
     * 없는 위치는 0으로 잡지 않고 평균 계산에서 완전히 제외한다 - 그래야 "액추에이터가 없어서 0분"이 "적게 가동해서 0분"과 섞이지 않는다.
     */
    private Map<String, MetricDiff> diffAgainstGroup(Map<String, Double> currentValues,
                                                     List<PeriodTelemetrySummary> others,
                                                     Function<PeriodTelemetrySummary, Map<String, Double>> extractor) {
        Map<String, MetricDiff> diffs = new LinkedHashMap<>();

        currentValues.forEach((key, thisValue) -> {
            List<Double> otherValues = others.stream()
                    .map(extractor)
                    .map(metrics -> metrics.get(key))
                    .filter(Objects::nonNull)
                    .toList();

            if (otherValues.isEmpty()) {
                return;
            }

            double groupAvg = otherValues.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            if (groupAvg == 0.0) {
                return;
            }

            double percentDiff = (thisValue - groupAvg) / groupAvg * 100.0;
            if (Math.abs(percentDiff) >= GROUP_COMPARISON_THRESHOLD_PERCENT) {
                diffs.put(key, new MetricDiff(thisValue, groupAvg, percentDiff));
            }
        });

        return diffs;
    }

    /**
     * 주간은 "8월 1주차 3층 회의실 리포트", 월간은 "8월 월간 3층 회의실 리포트" 형식의 제목을 만든다. 주차는 이번 기간의 시작일(월요일)이 그 달의 며칠째인지를 7로 나눠
     * 계산한다(1~7일=1주차, 8~14일=2주차, ...).
     *
     * @param periodStart  이번 기간 집계 시작(월요일 또는 매월 1일)
     * @param locationName Core에서 조회한 위치 이름
     * @param reportType   WEEKLY 또는 MONTHLY
     * @return 조립된 리포트 제목
     */
    private String buildTitle(OffsetDateTime periodStart, String locationName, ReportType reportType) {
        int month = periodStart.getMonthValue();

        if (reportType == ReportType.WEEKLY) {
            int weekOfMonth = ((periodStart.getDayOfMonth() - 1) / 7) + 1;
            return month + "월 " + weekOfMonth + "주차 " + locationName + " 리포트";
        }
        return month + "월 월간 " + locationName + " 리포트";
    }

    /**
     * 재집계된 데이터를 LLM 프롬프트 텍스트로 조립한다. 해석/평가 문구 없이 숫자·사실만 담아 토큰을 절약하고, 해석은 LLM이 출력 단계에서 직접 하도록 위임한다.
     *
     * @param reportType       WEEKLY 또는 MONTHLY
     * @param current          이번 기간 재집계 결과
     * @param previous         지난 기간 재집계 결과(증감 비교용)
     * @param alerts           이번 기간 엔진 알람 요약
     * @param suggestions      이번 기간 AI 제안 요약
     * @param actuatorCommands 이번 기간 액추에이터 조작 이력 요약(설정 온도, 조작 주체 비율)
     * @param groupComparison  같은 그룹 내 다른 위치 대비 비교 결과(±15% 이상 차이나는 항목만)
     * @param peakPatterns     월간 리포트에서만 채워지는 시간대별 패턴(주간은 항상 빈 리스트)
     * @param aiFlows          이 위치에 AI가 만든 자동화 목록("[AI] " 접두어만, 조회 실패 시 빈 리스트)
     * @param weather          현재 실외 날씨(aiFlows가 비어있거나 조회 실패 시 null) - 계절이 안 맞는 자동화를 짚어내는 용도
     * @return 조립된 프롬프트 텍스트
     */
    private String buildPrompt(ReportType reportType, PeriodTelemetrySummary current, PeriodTelemetrySummary previous,
                               EngineAlertSummary alerts, SuggestionSummary suggestions,
                               ActuatorCommandSummary actuatorCommands, GroupComparisonSummary groupComparison,
                               List<HourlyPeakPattern> peakPatterns, List<FlowSummaryResponse> aiFlows,
                               WeatherResponse weather) {

        StringBuilder sb = new StringBuilder();

        sb.append("당신은 스마트 오피스의 에너지 효율·실내 쾌적도를 진단하는 리포트 작성 전문가입니다.\n");
        sb.append("아래 데이터를 바탕으로 이번 기간(")
                .append(current.from()).append("~").append(current.to()).append(", ").append(reportType)
                .append(") 진단 리포트를 마크다운으로 작성하세요.\n\n");

        sb.append("## 쾌적 기준값 (알려진 지표만 명시, 그 외 지표는 일반적인 실내환경 기준으로 직접 판단하세요)\n");

        current.metricsAvg().keySet().forEach(metric -> {
            double[] range = COMFORT_RANGE.get(metric);
            if (range != null) {
                sb.append("- ").append(metric).append(": ").append(range[0]).append("~").append(range[1]).append("\n");
            }
        });

        sb.append("\n## 이번 기간 실내 환경\n");
        current.metricsAvg().forEach((metric, value) ->
                sb.append("- ").append(metric).append(" 평균: ").append(round1(value)).append("\n"));
        current.metricsMax().forEach((metric, value) ->
                sb.append("- ").append(metric).append(" 최고: ").append(round1(value)).append("\n"));
        current.metricsMin().forEach((metric, value) ->
                sb.append("- ").append(metric).append(" 최저: ").append(round1(value)).append("\n"));

        sb.append("\n## 지난 기간 대비\n");
        previous.metricsAvg().forEach((metric, prevValue) -> {
            Double currValue = current.metricsAvg().get(metric);
            if (currValue != null) {
                sb.append("- ").append(metric).append(" 평균: ").append(round1(prevValue)).append(" → ")
                        .append(round1(currValue)).append("\n");
            }
        });

        sb.append("\n## 액추에이터 가동 현황\n");
        current.actuatorOnMinutes().forEach((type, minutes) ->
                sb.append("- ").append(type).append(": ").append(round1(minutes)).append("분 가동\n"));

        // 시간대별 패턴은 월간 리포트에서만 채워져서 넘어온다(generateOneReport 참고)
        // 주간은 시간대별 샘플이 요일당 1개(7개)뿐이라 노이즈가 커서 제외 — 월간은 ~30개라 패턴으로 볼 만하다고 판단
        if (!peakPatterns.isEmpty()) {
            sb.append("\n## 시간대별 패턴 (기간 평균 대비 뚜렷하게 튀는 시간대)\n");
            peakPatterns.forEach(pattern ->
                    sb.append("- ").append(pattern.metric()).append(": ").append(pattern.peakHour())
                            .append("시경 평균 ").append(round1(pattern.peakValue())).append(" (기간 평균 ")
                            .append(round1(pattern.baselineAvg())).append(" 대비 +")
                            .append(round1(pattern.percentAboveBaseline())).append("%)\n"));
            sb.append("이 시간대 패턴을 활용해, 값이 오르기 전 시간대에 미리 액추에이터를 가동하는 등의 예방적 조치를 개선 제안에 포함하세요. ")
                    .append("이 패턴을 근거로 자동화 초안도 별도로 생성 요청됩니다.\n");
        }

        if (actuatorCommands.setTemperatureChangeCount() > 0) {
            sb.append("\n## 액추에이터 조작 이력\n");
            sb.append("- 설정 온도 변경: 총 ").append(actuatorCommands.setTemperatureChangeCount())
                    .append("회, 평균 설정값 ").append(round1(actuatorCommands.avgSetTemperature())).append("\n");
            actuatorCommands.executedByRatio().forEach((executedByType, ratio) ->
                    sb.append("- ").append(executedByType).append(" 조작 비율: ").append(ratio).append("%\n"));
        }

        sb.append("\n## 이상 알람\n");
        sb.append("- CRITICAL ").append(alerts.criticalCount()).append("건, WARNING ").append(alerts.warningCount())
                .append("건\n");
        if (!alerts.topAlertTitles().isEmpty()) {
            sb.append("- 주요 알람: ").append(String.join(", ", alerts.topAlertTitles())).append("\n");
        }

        sb.append("\n## AI 제안\n");
        sb.append("- 총 ").append(suggestions.totalCount()).append("건, 수락 ").append(suggestions.acceptedCount())
                .append("건, 거절 ").append(suggestions.rejectedCount())
                .append("건, 대기 ").append(suggestions.pendingCount()).append("건\n");

        boolean hasComparison = !groupComparison.metricDiffs().isEmpty() || !groupComparison.actuatorDiffs().isEmpty();
        if (hasComparison) {
            sb.append("\n## 그룹 내 다른 위치 대비 (같은 그룹 ").append(groupComparison.comparedLocationCount())
                    .append("곳 평균과 비교, ±").append((int) GROUP_COMPARISON_THRESHOLD_PERCENT)
                    .append("% 이상 차이만 표기)\n");
            groupComparison.metricDiffs().forEach((metric, diff) ->
                    sb.append("- ").append(metric).append(": 이 위치 ").append(round1(diff.thisValue()))
                            .append(" vs 그룹 평균 ").append(round1(diff.groupAvg())).append(" (")
                            .append(diff.percentDiff() > 0 ? "+" : "").append(round1(diff.percentDiff()))
                            .append("%)\n"));
            groupComparison.actuatorDiffs().forEach((type, diff) ->
                    sb.append("- ").append(type).append(" 가동시간: 이 위치 ").append(round1(diff.thisValue()))
                            .append("분 vs 그룹 평균 ").append(round1(diff.groupAvg())).append("분 (")
                            .append(diff.percentDiff() > 0 ? "+" : "").append(round1(diff.percentDiff()))
                            .append("%)\n"));
        }

        boolean hasAiFlows = hasAiFlows(aiFlows);
        boolean hasWeather = weather != null;
        if (hasAiFlows) {
            sb.append("\n## 관리 중인 자동화 (AI가 만든 flow)\n");
            if (hasWeather) {
                sb.append("(참고 - 현재 실외 기온: ").append(orNone(weather.temperature())).append("°C");
                if (weather.midTermAvgMaxTemp() != null || weather.midTermAvgMinTemp() != null) {
                    sb.append(", 4~10일 후 평균 기온 전망: ").append(orNone(weather.midTermAvgMinTemp()))
                            .append("~").append(orNone(weather.midTermAvgMaxTemp())).append("°C");
                }
                sb.append(")\n");
            }
            aiFlows.forEach(flow ->
                    sb.append("- ").append(flow.name()).append(" | 상태: ").append(flow.status())
                            .append(" | ").append(flow.description()).append("\n"));
        }

        sb.append("\n---\n다음 순서로 작성: 1) 요약(3줄 이내) 2) 실내 환경 진단 3) 에너지 사용 진단 ")
                .append("4) 지난 기간 대비 해석 5) 개선 제안")
                .append(hasComparison ? " 6) 그룹 내 비교" : "")
                .append(hasAiFlows ? " 7) 관리 중인 자동화 현황" : "").append(". ")
                .append("2)와 6)처럼 지표별 수치가 여러 개 나열되는 섹션은 문장으로 풀어쓰지 말고 마크다운 표로 ")
                .append("정리하세요(2번은 지표|평균|최고|최저|쾌적기준 컬럼, 6번은 지표|이 위치|그룹 평균|차이 컬럼). ")
                .append("표 아래에 해석은 2~3문장으로 짧게만 덧붙이고, 장황한 글머리 기호 나열은 피하세요. ")
                .append("제공된 수치만 사용하고 추측하지 마세요. 그룹 내 비교에서 차이가 있다는 사실은 명시하되, ")
                .append("원인은 제공된 데이터(액추에이터 가동시간 등) 범위 안에서만 언급하고 인원수·건물구조처럼 ")
                .append("데이터에 없는 요인은 단정하지 마세요. 개선 제안마다 기대 효과를 함께 제시하세요 - ")
                .append("제공된 데이터(액추에이터 가동시간, 설정 온도 이력, 그룹 내 비교)에서 근거를 찾을 수 있는 ")
                .append("범위에서 방향과 대략적인 크기로만 서술하고('가동시간이 줄어 에너지 사용이 다소 감소할 것으로 ")
                .append("예상됩니다' 등), 검증 불가능한 정확한 수치(예: '정확히 12.3% 감소')는 만들어내지 마세요. ")
                .append("4) 지난 기간 대비 섹션엔 표 대신 Mermaid xychart-beta 막대그래프를 하나 넣으세요 - 가장 ")
                .append("눈에 띄게 변한 지표 1개만 그래프로 그리고(단위가 다른 지표를 한 차트에 섞지 마세요), 나머지 ")
                .append("지표는 텍스트로 짧게 언급하세요. 형식 예시(지표명·단위·수치만 실제 값으로 바꿔서 그대로 따르세요):\n")
                .append("```mermaid\nxychart-beta\n    title \"온도 지난 기간 대비\"\n")
                .append("    x-axis [\"지난 기간\", \"이번 기간\"]\n    y-axis \"온도 (°C)\"\n    bar [22.5, 24.1]\n```")
                .append(hasComparison ? " 6) 그룹 내 비교는 표는 그대로 두되, 그룹 평균과 차이가 가장 큰 지표 1개를 "
                        + "표 위에 Mermaid xychart-beta 막대그래프로 추가하세요(이 위치 vs 그룹 평균, 막대 2개). "
                        + "형식 예시:\n```mermaid\nxychart-beta\n    title \"CO2 그룹 내 비교\"\n"
                        + "    x-axis [\"이 위치\", \"그룹 평균\"]\n    y-axis \"CO2 (ppm)\"\n    bar [850.0, 620.0]\n```" : "")
                .append(hasAiFlows ? " 관리 중인 자동화 섹션은 각 자동화가 무엇을 언제 하는지와 현재 상태(활성/비활성)를 "
                        + "간단히 나열하고, 제공된 설명 문장을 그대로 옮기지 말고 한 줄로 요약하세요." : "")
                .append(hasAiFlows && hasWeather ? " 설명 문구에서 유추되는 생성 시점(월/계절)과 현재·(있다면) 4~10일 후 "
                        + "기온 전망이 계절적으로 크게 안 맞으면(예: 여름철 냉방 자동화인데 지금도 앞으로도 겨울 수준 "
                        + "기온) 재검토가 필요하다고 짧게 덧붙이세요. 현재 기온만 반짝 다르고 전망은 원래 계절과 맞으면 "
                        + "하루짜리 변화일 수 있으니 언급하지 마세요. 전망 데이터가 없으면 현재 기온만으로 조심스럽게 판단하세요." : "");

        return sb.toString();
    }

    private String round1(double value) {
        return String.format("%.1f", value);
    }

    private String orNone(Object value) {
        return value != null ? String.valueOf(value) : "정보없음";
    }
}
