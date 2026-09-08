package com.insighton.ai.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ActuatorRunLogResponse(
        Long locationId,
        Long actuatorId,
        String actuatorType,
        String commandType,
        String commandValue,
        String executedByType,
        OffsetDateTime executedAt
) {
}
