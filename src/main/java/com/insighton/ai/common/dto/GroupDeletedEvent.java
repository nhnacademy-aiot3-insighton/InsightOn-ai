package com.insighton.ai.common.dto;

import java.util.List;

public record GroupDeletedEvent(
        Long groupId,
        List<Long> locationIds
) {
}