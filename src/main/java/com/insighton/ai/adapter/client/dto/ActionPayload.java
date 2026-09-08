package com.insighton.ai.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * AI가 판단한 액추에이터 조작 내용. SuggestionLog에 JSON으로 저장해두고 나중에(제안 수락 시, 또는 AI_DIRECT 자동 실행 시) 다시 읽어서 씀. 한 제안 안에서 여러 액추에이터를 동시에
 * 조작할 수 있도록 actions를 리스트로 둔다(예: 에어컨 끄고 환풍기 켜기).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionPayload(
        Long locationId,
        List<ActuatorAction> actions
) {
    public ActionPayload {
        actions = actions != null ? actions : List.of();
    }

    // actions 필드가 생기기 전(단일 액추에이터만 지원하던 시절)엔 actuatorType/command/commandValue가
    // 최상위에 있었음. 이 포맷으로 저장된 미결(is_accepted=null) 제안이 아직 남아있을 수 있어, actions가
    // 없을 때 옛 필드로 단일 action을 복원해 수락 시 실제 명령이 실행되도록 함(그냥 무시하면 사용자가
    // 수락했는데 아무 일도 안 일어나는 더 조용한 버그가 됨).
    @JsonCreator
    public static ActionPayload from(@JsonProperty("locationId") Long locationId,
                                     @JsonProperty("actions") List<ActuatorAction> actions,
                                     @JsonProperty("actuatorType") ActuatorType legacyActuatorType,
                                     @JsonProperty("command") String legacyCommand,
                                     @JsonProperty("commandValue") String legacyCommandValue) {
        if (actions == null && legacyActuatorType != null) {
            actions = List.of(new ActuatorAction(legacyActuatorType, legacyCommand, legacyCommandValue));
        }
        return new ActionPayload(locationId, actions);
    }
}
