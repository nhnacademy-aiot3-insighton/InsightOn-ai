package com.insighton.ai.domain.enginealert.exception;

public class EngineAlertNotFoundException extends RuntimeException {

    private final Long engineAlertId;

    public EngineAlertNotFoundException(Long engineAlertId) {
        super("EngineAlert not found: " + engineAlertId);
        this.engineAlertId = engineAlertId;
    }

    public Long getEngineAlertId() {
        return engineAlertId;
    }
}