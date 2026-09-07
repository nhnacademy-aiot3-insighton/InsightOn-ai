package com.insighton.ai.adapter.client.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActuatorNotFoundExceptionTest {

    @Test
    void 위치ID와_액추에이터_타입을_메시지에_포함한다() {
        ActuatorNotFoundException exception = new ActuatorNotFoundException(42L, "AIRCON");

        assertThat(exception.getMessage()).isEqualTo("위치 42에 AIRCON 액추에이터가 없습니다.");
    }
}
