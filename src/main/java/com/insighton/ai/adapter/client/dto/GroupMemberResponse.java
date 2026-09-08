package com.insighton.ai.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.insighton.ai.adapter.client.dto.GroupRole;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupMemberResponse(Long groupId, GroupRole groupRole) {
}
