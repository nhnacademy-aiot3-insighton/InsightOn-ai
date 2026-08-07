package com.insighton.ai.suggestion.dto;

import com.insighton.ai.coreapi.domain.ActuatorType;

public record ActuatorAction(
        ActuatorType actuatorType,
        String command,
        String commandValue
) {
}
