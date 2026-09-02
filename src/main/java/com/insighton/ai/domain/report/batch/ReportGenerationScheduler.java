package com.insighton.ai.domain.report.batch;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.FlowDraftRequester;
import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.ActuatorCommandSummary;
import com.insighton.ai.adapter.client.dto.ActuatorRunLogResponse;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.domain.enginealert.dto.EngineAlertSummary;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
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

    // Core com.insighton.core.domain.actuators.policy의 CommandType/CommandValueRule 확정값과 동일하게 유지할 것
    // (SuggestionGenerationScheduler.ACTUATOR_COMMANDS와 같은 값 - flow 초안 판단용 프롬프트에도 같은 허용 어휘가 필요해 중복 선언)
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
    private final EngineAlertService engineAlertService;
    private final SuggestionLogService suggestionLogService;
    private final CoreClient coreClient;
    private final ReportService reportService;
    private final ChatClient chatClient;
    private final FlowDraftRequester flowDraftRequester;

    // TEST ONLY: 30분마다 도는 테스트용 cron. 원래 값 "0 0 0 * * MON" (매주 월요일 00:00)로 되돌리고 커밋할 것.
    /**
     * 매주 월요일 00:00 실행. 직전 월~일(7일)을 이번 기간, 그 전 7일을 비교 기준(지난 기간)
     */
    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "weeklyReportGeneration", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void generateWeeklyReports() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS);

        generateReports(ReportType.WEEKLY, now.minusWeeks(1), now.minusHours(1),
                now.minusWeeks(2), now.minusWeeks(1).minusHours(1));
    }

    // TEST ONLY: 30분마다 도는 테스트용 cron. 원래 값 "0 0 0 1 * *" (매월 1일 00:00)로 되돌리고 커밋할 것.
    /**
     * 매월 1일 00:00 실행. 직전 달 1일~말일을 이번 기간, 그 전달을 비교 기준(지난 기간)
     */
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

        String prompt = buildPrompt(reportType, current, previous, alerts, suggestions, actuatorCommands,
                groupComparison, peakPatterns);

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
     * 시간대별 패턴이 있으면 LLM에게 "예방적 자동화가 적절한지, 적절하다면 어떤 액추에이터를 어떻게 조작할지"를 직접 판단시킨 뒤, 그 판단대로만 Rule Engine에 flow 초안 생성을 요청한다 -
     * 어떤 조작을 할지를 고정된 매핑표로 미리 정해두면 AI 판단이 아니라 규칙표 조회가 되어버리므로, 매번 상황(패턴 크기, 위치에 실제 있는 액추에이터)을 보고 LLM이 결정하게 한다. 이 위치에
     * 액추에이터가 하나도 없으면 LLM 호출 자체를 건너뛴다. 실패는 {@link FlowDraftRequester}가 자체적으로 흡수하므로 여기선 별도 try-catch가 필요 없다.
     */
    private void requestFlowDrafts(Long groupId, Long locationId, Long reportId, String reportTitle,
                                   PeriodTelemetrySummary current, List<HourlyPeakPattern> peakPatterns) {
        Set<String> presentActuatorTypes = current.actuatorOnMinutes().keySet();
        if (peakPatterns.isEmpty() || presentActuatorTypes.isEmpty()) {
            return;
        }

        String prompt = buildFlowActionPrompt(peakPatterns, presentActuatorTypes);
        FlowActionDecisions result = chatClient.prompt().user(prompt).call().entity(FlowActionDecisions.class);

        Map<String, HourlyPeakPattern> patternsByMetric = peakPatterns.stream()
                .collect(Collectors.toMap(HourlyPeakPattern::metric, Function.identity(), (a, b) -> a));

        for (FlowActionDecision decision : result.decisions()) {
            HourlyPeakPattern pattern = patternsByMetric.get(decision.metric());
            boolean actuatorPresent = decision.actuatorType() != null
                    && presentActuatorTypes.contains(decision.actuatorType().name());
            if (!decision.automationRecommended() || pattern == null || !actuatorPresent) {
                continue;
            }
            ActuatorAction action = new ActuatorAction(decision.actuatorType(), decision.command(),
                    decision.commandValue());
            flowDraftRequester.requestDraft(groupId, locationId, reportId, reportTitle, pattern, action);
        }
    }

    /**
     * 시간대별 패턴별로 예방적 자동화 여부와 구체적인 명령을 LLM이 판단하도록 조립하는 프롬프트. 이 위치에 실제로 있는 액추에이터와 허용 명령만 보여줘서, 존재하지 않거나 지원하지 않는 조합을 LLM이
     * 지어내지 못하게 한다(SuggestionGenerationScheduler.buildCommonContext()와 동일 원칙).
     */
    private String buildFlowActionPrompt(List<HourlyPeakPattern> peakPatterns, Set<String> presentActuatorTypes) {
        StringBuilder sb = new StringBuilder();

        sb.append("당신은 스마트 오피스 자동화 규칙을 설계하는 AI입니다.\n");
        sb.append("아래 시간대별 패턴 각각에 대해, 피크 시간 전에 미리 액추에이터를 조작하는 예방적 자동화가 적절한지 판단하세요.\n\n");

        sb.append("## 시간대별 패턴\n");
        peakPatterns.forEach(pattern ->
                sb.append("- 지표: ").append(pattern.metric()).append(", 피크 시간: ").append(pattern.peakHour())
                        .append("시경, 피크값: ").append(round1(pattern.peakValue())).append(", 기간 평균: ")
                        .append(round1(pattern.baselineAvg())).append(", 평균 대비 +")
                        .append(round1(pattern.percentAboveBaseline())).append("%\n"));

        sb.append("\n## 이 위치에 실제로 있는 액추에이터와 허용 명령 (이 목록 안에서만 선택)\n");
        ACTUATOR_COMMANDS.forEach((type, commands) -> {
            if (!presentActuatorTypes.contains(type)) {
                return;
            }
            sb.append("- ").append(type).append("\n");
            commands.forEach((command, allowedValues) ->
                    sb.append("  - ").append(command).append(": ").append(allowedValues).append("\n"));
        });

        sb.append("\n---\n");
        sb.append("패턴마다 하나씩 판단하세요. actuatorType/command는 반드시 위 목록에 있는 조합만 쓰세요. ")
                .append("이 위치에 대응할 만한 액추에이터가 없거나, 자동화보다 사용자 개입이 더 적절하다고 판단되면 ")
                .append("automationRecommended=false로 하고 나머지 필드는 비우세요.");

        return sb.toString();
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
                .filter(summary -> !summary.metricsAvg().isEmpty())
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
     * @return 조립된 프롬프트 텍스트
     */
    private String buildPrompt(ReportType reportType, PeriodTelemetrySummary current, PeriodTelemetrySummary previous,
                               EngineAlertSummary alerts, SuggestionSummary suggestions,
                               ActuatorCommandSummary actuatorCommands, GroupComparisonSummary groupComparison,
                               List<HourlyPeakPattern> peakPatterns) {

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

        sb.append("\n---\n다음 순서로 작성: 1) 요약(3줄 이내) 2) 실내 환경 진단 3) 에너지 사용 진단 ")
                .append("4) 지난 기간 대비 해석 5) 개선 제안")
                .append(hasComparison ? " 6) 그룹 내 비교" : "").append(". ")
                .append("2)와 6)처럼 지표별 수치가 여러 개 나열되는 섹션은 문장으로 풀어쓰지 말고 마크다운 표로 ")
                .append("정리하세요(2번은 지표|평균|최고|최저|쾌적기준 컬럼, 6번은 지표|이 위치|그룹 평균|차이 컬럼). ")
                .append("표 아래에 해석은 2~3문장으로 짧게만 덧붙이고, 장황한 글머리 기호 나열은 피하세요. ")
                .append("제공된 수치만 사용하고 추측하지 마세요. 그룹 내 비교에서 차이가 있다는 사실은 명시하되, ")
                .append("원인은 제공된 데이터(액추에이터 가동시간 등) 범위 안에서만 언급하고 인원수·건물구조처럼 ")
                .append("데이터에 없는 요인은 단정하지 마세요. 개선 제안마다 기대 효과를 함께 제시하세요 - ")
                .append("제공된 데이터(액추에이터 가동시간, 설정 온도 이력, 그룹 내 비교)에서 근거를 찾을 수 있는 ")
                .append("범위에서 방향과 대략적인 크기로만 서술하고('가동시간이 줄어 에너지 사용이 다소 감소할 것으로 ")
                .append("예상됩니다' 등), 검증 불가능한 정확한 수치(예: '정확히 12.3% 감소')는 만들어내지 마세요.");

        return sb.toString();
    }

    private String round1(double value) {
        return String.format("%.1f", value);
    }
}
