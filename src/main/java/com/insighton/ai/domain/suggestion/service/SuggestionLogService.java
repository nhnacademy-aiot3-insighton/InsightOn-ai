package com.insighton.ai.domain.suggestion.service;

import com.insighton.ai.domain.suggestion.dto.SuggestionLogCreateRequest;
import com.insighton.ai.domain.suggestion.dto.SuggestionLogResponse;
import com.insighton.ai.domain.suggestion.dto.SuggestionSummary;
import java.time.OffsetDateTime;
import java.util.List;

public interface SuggestionLogService {
    List<SuggestionLogResponse> findSuggestionLogs(Long groupId, Long locationId);

    SuggestionLogResponse findSuggestionLog(Long suggestionLogId, Long userId);

    SuggestionLogResponse create(SuggestionLogCreateRequest request);

    SuggestionLogResponse accept(Long suggestionLogId, Long userId);

    SuggestionLogResponse reject(Long suggestionLogId, Long userId);

    void deleteByGroup(Long groupId);

    void deleteByLocation(Long locationId);

    SuggestionSummary summarizePeriod(Long locationId, OffsetDateTime from, OffsetDateTime to);
}