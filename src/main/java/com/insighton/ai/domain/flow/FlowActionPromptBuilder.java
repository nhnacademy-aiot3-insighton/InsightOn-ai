package com.insighton.ai.domain.flow;

import static com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary.ACTUATOR_COMMANDS;

import com.insighton.ai.domain.telemetrystats.dto.HourlyPeakPattern;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 시간대별 패턴을 보고 예방적 자동화가 적절한지/어떤 명령인지 LLM이 판단하도록 조립하는 프롬프트.
 * ReportGenerationScheduler(리포트 기반)와 FlowRecommendationChatTool(챗봇 요청 기반) 둘 다 사용 -
 * 원래 ReportGenerationScheduler 안에 있던 buildFlowActionPrompt()를 그대로 뽑아온 것.
 */
@Component
public class FlowActionPromptBuilder {

    public String build(List<HourlyPeakPattern> peakPatterns, Set<String> presentActuatorTypes) {
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

    private String round1(double value) {
        return String.format("%.1f", value);
    }
}
