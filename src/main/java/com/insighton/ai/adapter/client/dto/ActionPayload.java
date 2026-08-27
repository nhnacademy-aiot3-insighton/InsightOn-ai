package com.insighton.ai.adapter.client.dto;

import java.util.List;

/**
 * AI가 판단한 액추에이터 조작 내용. SuggestionLog에 JSON으로 저장해두고 나중에(제안 수락 시, 또는 AI_DIRECT 자동 실행 시) 다시 읽어서 씀. 한 제안 안에서 여러 액추에이터를 동시에
 * 조작할 수 있도록 actions를 리스트로 둔다(예: 에어컨 끄고 환풍기 켜기).
 */
public record ActionPayload(
        Long locationId,
        List<ActuatorAction> actions
) {
    // 이 필드가 생기기 전에 저장된 SuggestionLog JSON(actuatorType/command/commandValue가 최상위)엔
    // "actions" 키가 없어서, 그걸 그대로 역직렬화하면 null이 됨 - 호출부마다 null 체크하는 대신 여기서 한 번에 방어.
    public ActionPayload {
        actions = actions != null ? actions : List.of();
    }
}
