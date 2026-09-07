package com.insighton.ai.adapter.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;

import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.ActuatorCommandRequest;
import com.insighton.ai.adapter.client.dto.ActuatorType;
import com.insighton.ai.adapter.client.dto.CallerService;
import com.insighton.ai.adapter.client.exception.ActuatorNotFoundException;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActuatorCommandExecutorTest {

    @Mock
    private CoreClient coreClient;

    private ActuatorCommandExecutor actuatorCommandExecutor;

    @BeforeEach
    void setUp() {
        actuatorCommandExecutor = new ActuatorCommandExecutor(coreClient);
    }

    @Test
    void execute_액션마다_CoreClient에_AI_SYSTEM_주체로_명령을_전달한다() {
        List<ActuatorAction> actions = List.of(
                new ActuatorAction(ActuatorType.AIRCON, "POWER_STATUS", "ON"),
                new ActuatorAction(ActuatorType.AIR_PURIFIER, "POWER_STATUS", "OFF"));

        actuatorCommandExecutor.execute(5L, 42L, actions, CallerService.AI_SYSTEM);

        Mockito.verify(coreClient).executeActuatorCommand(5L, 42L,
                ActuatorCommandRequest.of("AIRCON", "POWER_STATUS", "ON", CallerService.AI_SYSTEM));
        Mockito.verify(coreClient).executeActuatorCommand(5L, 42L,
                ActuatorCommandRequest.of("AIR_PURIFIER", "POWER_STATUS", "OFF", CallerService.AI_SYSTEM));
    }

    @Test
    void execute_Core가_404를_반환하면_ActuatorNotFoundException으로_변환한다() {
        willThrow(feignNotFound()).given(coreClient)
                .executeActuatorCommand(eq(5L), eq(42L), any(ActuatorCommandRequest.class));
        List<ActuatorAction> actions = List.of(new ActuatorAction(ActuatorType.AIRCON, "POWER_STATUS", "ON"));

        assertThatThrownBy(() -> actuatorCommandExecutor.execute(5L, 42L, actions, CallerService.AI_SYSTEM))
                .isInstanceOf(ActuatorNotFoundException.class)
                .hasMessageContaining("AIRCON");
    }

    private FeignException.NotFound feignNotFound() {
        Request request = Request.create(HttpMethod.PUT, "/internal/v1/groups/5/locations/42/actuators/state",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("not found", request, null, null);
    }
}
