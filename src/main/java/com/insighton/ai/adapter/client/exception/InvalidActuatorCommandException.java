package com.insighton.ai.adapter.client.exception;

public class InvalidActuatorCommandException extends RuntimeException {

    public InvalidActuatorCommandException(String actuatorType, String command, String commandValue) {
        super("허용되지 않은 액추에이터 명령입니다: " + actuatorType + "." + command + "=" + commandValue);
    }
}
