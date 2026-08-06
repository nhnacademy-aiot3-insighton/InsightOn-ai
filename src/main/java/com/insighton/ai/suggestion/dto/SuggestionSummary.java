package com.insighton.ai.suggestion.dto;

public record SuggestionSummary(
        long totalCount,
        long acceptedCount,
        long rejectedCount,
        long pendingCount
) {
}
