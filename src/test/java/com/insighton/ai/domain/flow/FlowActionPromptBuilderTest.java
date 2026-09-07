package com.insighton.ai.domain.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.insighton.ai.domain.telemetrystats.dto.HourlyPeakPattern;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FlowActionPromptBuilderTest {

    private final FlowActionPromptBuilder flowActionPromptBuilder = new FlowActionPromptBuilder();

    @Test
    void build_피크_패턴을_지표_피크시간_피크값_평균_대비_비율과_함께_나열한다() {
        List<HourlyPeakPattern> patterns = List.of(
                new HourlyPeakPattern("temperature", 13, 30.0, 24.0, 25.0));

        String prompt = flowActionPromptBuilder.build(patterns, Set.of("AIRCON"));

        assertThat(prompt)
                .contains("## 시간대별 패턴")
                .contains("지표: temperature, 피크 시간: 13시경, 피크값: 30.0, 기간 평균: 24.0, 평균 대비 +25.0%");
    }

    @Test
    void build_실제_존재하는_액추에이터_타입만_허용_명령_목록에_포함한다() {
        String prompt = flowActionPromptBuilder.build(List.of(), Set.of("AIRCON"));

        assertThat(prompt).contains("AIRCON");
        assertThat(prompt).doesNotContain("AIR_PURIFIER");
        assertThat(prompt).doesNotContain("VENTILATION_FAN");
    }

    @Test
    void build_존재하는_액추에이터가_없으면_허용_명령_목록이_비어있다() {
        String prompt = flowActionPromptBuilder.build(List.of(), Set.of());

        assertThat(prompt)
                .contains("## 이 위치에 실제로 있는 액추에이터와 허용 명령 (이 목록 안에서만 선택)")
                .doesNotContain("AIRCON")
                .doesNotContain("AIR_PURIFIER")
                .doesNotContain("VENTILATION_FAN");
    }

    @Test
    void build_판단_지침과_automationRecommended_false_안내_문구가_포함된다() {
        String prompt = flowActionPromptBuilder.build(List.of(), Set.of());

        assertThat(prompt)
                .contains("패턴마다 하나씩 판단하세요")
                .contains("automationRecommended=false로 하고 나머지 필드는 비우세요");
    }
}
