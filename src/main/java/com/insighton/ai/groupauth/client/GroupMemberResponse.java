package com.insighton.ai.groupauth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.insighton.ai.groupauth.domain.GroupRole;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupMemberResponse(Long groupId, GroupRole groupRole) {
}
