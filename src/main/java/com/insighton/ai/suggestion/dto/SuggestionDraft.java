package com.insighton.ai.suggestion.dto;

import java.util.List;

/**
 * LLM이 생성하는 제안 초안(구조화 출력). actionNeeded=false면 조치 불필요로 판단해 제안 자체를 저장하지 않고, true면 title/suggestionText와 함께 실제 조작 가능한
 * 액추에이터 명령(actuatorType/command/commandValue)을 담는다. 동시에 조작해야 하면 actions에 여러개 담기 가능 창문 개방처럼 액추에이터가 아닌 조언은
 * suggestionText에만 담기고 actuatorType은 비워질 수 있다.
 */
public record SuggestionDraft(
        boolean actionNeeded,
        String title,
        String suggestionText,
        List<ActuatorAction> actions
) {
}
