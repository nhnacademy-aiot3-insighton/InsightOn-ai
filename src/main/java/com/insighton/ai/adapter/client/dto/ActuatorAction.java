package com.insighton.ai.adapter.client.dto;

/**
 * AI제안에서 여러 액추체이터를 동시에 조작할 수 있도록, 명령 하나를 나타내는 단위
 */
public record ActuatorAction(
        ActuatorType actuatorType,
        String command,
        String commandValue
) {
}
