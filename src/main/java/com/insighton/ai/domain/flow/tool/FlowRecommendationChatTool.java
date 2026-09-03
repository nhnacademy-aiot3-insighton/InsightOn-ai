package com.insighton.ai.domain.flow.tool;

import static com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary.BUSINESS_HOUR_END;
import static com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary.BUSINESS_HOUR_START;

import com.insighton.ai.adapter.client.FlowDraftRequester;
import com.insighton.ai.adapter.client.LocationResolver;
import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.domain.flow.FlowActionPromptBuilder;
import com.insighton.ai.domain.report.dto.FlowActionDecision;
import com.insighton.ai.domain.report.dto.FlowActionDecisions;
import com.insighton.ai.domain.telemetrystats.dto.HourlyPeakPattern;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * ReportGenerationScheduler.requestFlowDrafts()와 동일한 로직(시간대별 패턴 재추출 → 업무시간 필터 →
 * LLM 판단 → FlowDraftRequester)을 챗봇 요청으로 즉시 실행한다. 리포트가 나올 때까지 기다리지 않고
 * "이 방 자동화 만들어줘" 같은 요청에 바로 응답하기 위함.
 */
@Component
@RequiredArgsConstructor
public class FlowRecommendationChatTool {

    private static final String NO_LOCATION_MESSAGE = "이 대화에서 어느 위치를 말하는지 알 수 없어 자동화를 추천할 수 없습니다. "
            + "사용자에게 어느 위치인지 물어보세요.";
    private static final int LOOKBACK_DAYS = 30;

    private final LocationResolver locationResolver;
    private final HourlyTelemetryStatService hourlyTelemetryStatService;
    private final FlowActionPromptBuilder flowActionPromptBuilder;
    private final FlowDraftRequester flowDraftRequester;
    private final ChatClient chatClient;

    @Tool(description = "이 위치에 적절한 예방적 자동화(flow)를 AI가 최근 데이터를 분석해서 직접 만든다. "
            + "사용자가 세부 조건을 지정하지 않고 '이 방 자동화 만들어줘' 같이 요청하면 이 도구를 쓴다. "
            + "업무시간(9~17시) 패턴만 대상으로 하고, 위치가 AI_DIRECT 모드면 즉시 활성화되고 "
            + "SUGGESTION 모드면 비활성 상태로 생성돼 대시보드에서 활성화해야 한다(결과 문구에 실제 상태가 포함됨).")
    public String createRecommendedFlow(
            @ToolParam(description = "대상 위치 이름. 안 주면 대화의 현재 위치 사용", required = false) String locationName,
            ToolContext toolContext) {

        Long groupId = (Long) toolContext.getContext().get("groupId");
        Long contextLocationId = (Long) toolContext.getContext().get("locationId");

        Long locationId;
        if (locationName != null) {
            Optional<Long> resolved = locationResolver.resolveIdByName(groupId, locationName);
            if (resolved.isEmpty()) {
                return "위치를 찾을 수 없습니다: " + locationName;
            }
            locationId = resolved.get();
        } else {
            locationId = contextLocationId;
        }
        if (locationId == null) {
            return NO_LOCATION_MESSAGE;
        }

        OffsetDateTime now = OffsetDateTime.now();
        PeriodTelemetrySummary recent = hourlyTelemetryStatService.summarizePeriod(
                locationId, now.minusDays(LOOKBACK_DAYS), now);
        if (recent.metricsAvg().isEmpty()) {
            return "최근 " + LOOKBACK_DAYS + "일간 센서 데이터가 없어 자동화를 추천할 수 없습니다.";
        }

        List<HourlyPeakPattern> businessHourPatterns = hourlyTelemetryStatService.extractPeakPatterns(recent).stream()
                .filter(pattern -> pattern.peakHour() >= BUSINESS_HOUR_START && pattern.peakHour() <= BUSINESS_HOUR_END)
                .toList();
        Set<String> presentActuatorTypes = recent.actuatorOnMinutes().keySet();

        if (businessHourPatterns.isEmpty() || presentActuatorTypes.isEmpty()) {
            return "최근 데이터에서 업무시간 내 뚜렷한 패턴이나 조작 가능한 액추에이터를 찾지 못해 추천할 자동화가 없습니다.";
        }

        String prompt = flowActionPromptBuilder.build(businessHourPatterns, presentActuatorTypes);
        FlowActionDecisions result = chatClient.prompt().user(prompt).call().entity(FlowActionDecisions.class);

        Map<String, HourlyPeakPattern> patternsByMetric = businessHourPatterns.stream()
                .collect(Collectors.toMap(HourlyPeakPattern::metric, Function.identity(), (a, b) -> a));

        List<String> summaries = new ArrayList<>();
        for (FlowActionDecision decision : result.decisions()) {
            HourlyPeakPattern pattern = patternsByMetric.get(decision.metric());
            boolean actuatorPresent = decision.actuatorType() != null
                    && presentActuatorTypes.contains(decision.actuatorType().name());
            if (!decision.automationRecommended() || pattern == null || !actuatorPresent) {
                continue;
            }
            ActuatorAction action = new ActuatorAction(decision.actuatorType(), decision.command(),
                    decision.commandValue());
            Optional<String> status = flowDraftRequester.requestDraft(groupId, locationId, "챗봇 요청", pattern, action);
            summaries.add(summarize(pattern, action, status));
        }

        if (summaries.isEmpty()) {
            return "분석 결과 지금 이 위치에 추가로 필요한 자동화가 없습니다.";
        }
        return summaries.size() + "개의 자동화를 만들었습니다.\n" + String.join("\n", summaries);
    }

    // status: Rule Engine이 실제로 저장한 상태(ACTIVE/INACTIVE) - 위치가 AI_DIRECT 모드면 즉시 ACTIVE로
    // 자동 활성화되므로, "항상 비활성"이라고 단정하면 안 됨. 요청 자체가 실패했으면(빈 값) 상태를 알 수 없음.
    private String summarize(HourlyPeakPattern pattern, ActuatorAction action, Optional<String> status) {
        String activationNote = status
                .map(s -> "ACTIVE".equals(s) ? " (즉시 활성화됨)" : " (비활성 상태 - 대시보드에서 활성화 필요)")
                .orElse(" (생성 여부 확인 필요 - Rule Engine 요청 실패)");
        return "- %s이(가) %d시경 평균보다 %.0f%% 높게 반복 관측돼, %s의 %s을(를) %s(으)로 맞추는 자동화를 만들었습니다.%s"
                .formatted(pattern.metric(), pattern.peakHour(), pattern.percentAboveBaseline(),
                        action.actuatorType(), action.command(), action.commandValue(), activationNote);
    }
}
