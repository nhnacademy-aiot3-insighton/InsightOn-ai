package com.insighton.ai.adapter.rabbitmq.dto;

import java.util.List;

public record GroupDeletedEvent(
        Long groupId,
        List<Long> locationIds
) {
}