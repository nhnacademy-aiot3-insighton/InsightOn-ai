package com.insighton.ai.domain.report.batch;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.ActuatorCommandSummary;
import com.insighton.ai.adapter.client.dto.ActuatorRunLogResponse;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.domain.enginealert.dto.EngineAlertSummary;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
import com.insighton.ai.domain.report.dto.ReportCreateRequest;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.service.ReportService;
import com.insighton.ai.domain.suggestion.dto.SuggestionSummary;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final HourlyTelemetryStatService hourlyTelemetryStatService;
    private final EngineAlertService engineAlertService;
    private final SuggestionLogService suggestionLogService;
    private final CoreClient coreClient;
    private final ReportService reportService;
    private final ChatClient chatClient;

    /**
     * 매주 월요일 00:00 실행. 직전 월~일(7일)을 이번 기간, 그 전 7일을 비교 기준(지난 기간)
     */
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    @SchedulerLock(name = "weeklyReportGeneration", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void generateWeeklyReports() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS);

        generateReports(ReportType.WEEKLY, now.minusWeeks(1), now.minusHours(1),
                now.minusWeeks(2), now.minusWeeks(1).minusHours(1));
    }

    /**
     * 매월 1일 00:00 실행. 직전 달 1일~말일을 이번 기간, 그 전달을 비교 기준(지난 기간)
     */
    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
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

        String prompt = buildPrompt(reportType, current, previous, alerts, suggestions, actuatorCommands);

        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        String title = buildTitle(periodStart, location.locationName(), reportType);

        reportService.createReport(new ReportCreateRequest(
                location.groupId(), locationId, title, reportType, content
        ));

        log.info("리포트 생성 - reportType:{}, locationId:{}", reportType, locationId);
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
     * @return 조립된 프롬프트 텍스트
     */
    private String buildPrompt(ReportType reportType, PeriodTelemetrySummary current, PeriodTelemetrySummary previous,
                               EngineAlertSummary alerts, SuggestionSummary suggestions,
                               ActuatorCommandSummary actuatorCommands) {

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

        // 월간 리포트에서만 시간대별 패턴(지표별 최고치 시간대)을 프롬프트에 포함
        // 주간은 시간대별 샘플이 요일당 1개(7개)뿐이라 노이즈가 커서 제외 — 월간은 ~30개라 패턴으로 볼 만하다고 판단
        // 24시간 전체가 아니라 지표별 "가장 높은 시간대 1개"만 뽑아서 토큰 비용을 아끼기..
        if (reportType == ReportType.MONTHLY) {
            sb.append("\n## 시간대별 패턴 (지표별 최고치 시간대)\n");
            current.hourlyAvgByMetric().forEach((metric, hourlyAvg) ->
                    hourlyAvg.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .ifPresent(peak -> sb.append("- ").append(metric).append(": ")
                                    .append(peak.getKey()).append("시경 평균 ").append(round1(peak.getValue()))
                                    .append("로 가장 높음\n")));
            sb.append("이 시간대 패턴을 활용해, 값이 오르기 전 시간대에 미리 액추에이터를 가동하는 등의 예방적 조치를 개선 제안에 포함하세요.\n");
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

        sb.append("\n---\n다음 순서로 작성: 1) 요약(3줄 이내) 2) 실내 환경 진단 3) 에너지 사용 진단 ")
                .append("4) 지난 기간 대비 해석 5) 개선 제안. 제공된 수치만 사용하고 추측하지 마세요.");

        return sb.toString();
    }

    private String round1(double value) {
        return String.format("%.1f", value);
    }
}
