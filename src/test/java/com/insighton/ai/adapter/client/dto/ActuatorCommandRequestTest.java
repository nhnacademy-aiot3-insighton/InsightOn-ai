package com.insighton.ai.adapter.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActuatorCommandRequestTest {

    @Test
    void of_POWER_STATUS는_power로_변환된다() {
        ActuatorCommandRequest request = ActuatorCommandRequest.of("AIRCON", "POWER_STATUS", "ON",
                CallerService.AI_SYSTEM);

        assertThat(request.command()).isEqualTo("power");
    }

    @Test
    void of_OPERATION_MODE는_mode로_변환된다() {
        ActuatorCommandRequest request = ActuatorCommandRequest.of("AIRCON", "OPERATION_MODE", "COOL",
                CallerService.AI_SYSTEM);

        assertThat(request.command()).isEqualTo("mode");
    }

    @Test
    void of_SET_TEMPERATURE는_temperature로_변환된다() {
        ActuatorCommandRequest request = ActuatorCommandRequest.of("AIRCON", "SET_TEMPERATURE", "23",
                CallerService.AI_SYSTEM);

        assertThat(request.command()).isEqualTo("temperature");
    }

    @Test
    void of_매핑에_없는_값은_그대로_통과시킨다() {
        ActuatorCommandRequest request = ActuatorCommandRequest.of("AIRCON", "unknown", "1",
                CallerService.AI_SYSTEM);

        assertThat(request.command()).isEqualTo("unknown");
    }
}
