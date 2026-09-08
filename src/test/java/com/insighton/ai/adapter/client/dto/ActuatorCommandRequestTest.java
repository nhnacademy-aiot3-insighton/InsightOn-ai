package com.insighton.ai.adapter.client.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insighton.ai.adapter.client.exception.InvalidActuatorCommandException;
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
    void of_허용되지_않은_명령은_거부한다() {
        assertThatThrownBy(() -> ActuatorCommandRequest.of("AIRCON", "unknown", "1", CallerService.AI_SYSTEM))
                .isInstanceOf(InvalidActuatorCommandException.class);
    }

    @Test
    void of_허용_범위_밖의_값은_거부한다() {
        assertThatThrownBy(() -> ActuatorCommandRequest.of("AIRCON", "SET_TEMPERATURE", "999", CallerService.AI_SYSTEM))
                .isInstanceOf(InvalidActuatorCommandException.class);
    }

    @Test
    void of_존재하지_않는_액추에이터_타입은_거부한다() {
        assertThatThrownBy(() -> ActuatorCommandRequest.of("HEATER", "POWER_STATUS", "ON", CallerService.AI_SYSTEM))
                .isInstanceOf(InvalidActuatorCommandException.class);
    }
}
