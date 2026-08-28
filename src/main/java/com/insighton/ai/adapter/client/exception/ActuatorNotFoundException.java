package com.insighton.ai.adapter.client.exception;

public class ActuatorNotFoundException extends RuntimeException {

    public ActuatorNotFoundException(Long locationId, String actuatorType) {
        super("위치 " + locationId + "에 " + actuatorType + " 액추에이터가 없습니다.");
    }
}