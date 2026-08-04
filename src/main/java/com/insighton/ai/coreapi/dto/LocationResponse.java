package com.insighton.ai.coreapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationResponse(
        Long locationId,
        String locationName,
        Long groupId) {
}