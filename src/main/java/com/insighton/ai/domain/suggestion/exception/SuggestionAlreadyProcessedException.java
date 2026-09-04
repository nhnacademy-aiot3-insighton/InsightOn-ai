package com.insighton.ai.domain.suggestion.exception;

public class SuggestionAlreadyProcessedException extends RuntimeException {

    public SuggestionAlreadyProcessedException(Long suggestionLogId) {
        super("이미 처리된 제안입니다: " + suggestionLogId);
    }
}
