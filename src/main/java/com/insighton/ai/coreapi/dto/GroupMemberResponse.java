package com.insighton.ai.coreapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.insighton.ai.coreapi.domain.GroupRole;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupMemberResponse(Long groupId, GroupRole groupRole) {
}
