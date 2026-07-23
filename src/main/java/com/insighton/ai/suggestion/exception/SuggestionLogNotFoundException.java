package com.insighton.ai.suggestion.exception;

public class SuggestionLogNotFoundException extends RuntimeException {

    private final Long suggestionLogId;

    public SuggestionLogNotFoundException(Long suggestionLogId) {
        super("SuggestionLog not found: " + suggestionLogId);
        this.suggestionLogId = suggestionLogId;
    }

    public Long getSuggestionLogId() {
        return suggestionLogId;
    }
}