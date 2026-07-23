package com.insighton.ai.suggestion.service;

import com.insighton.ai.suggestion.dto.SuggestionLogCreateRequest;
import com.insighton.ai.suggestion.dto.SuggestionLogResponse;
import java.util.List;

public interface SuggestionLogService {
    List<SuggestionLogResponse> findSuggestionLogs(Long groupId, Long locationId);

    SuggestionLogResponse findSuggestionLog(Long suggestionLogId);

    SuggestionLogResponse create(SuggestionLogCreateRequest request);

    SuggestionLogResponse accept(Long suggestionLogId);
    
    SuggestionLogResponse reject(Long suggestionLogId);
}