package com.insighton.ai.adapter.client;

import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.ActuatorCommandRequest;
import com.insighton.ai.adapter.client.dto.CallerService;
import com.insighton.ai.adapter.client.exception.ActuatorNotFoundException;
import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ActuatorCommandExecutor {

    private final CoreClient coreClient;

    public void execute(Long locationId, List<ActuatorAction> actions, CallerService callerService) {

        for (ActuatorAction action : actions) {
            try {
                coreClient.executeActuatorCommand(locationId,
                        ActuatorCommandRequest.of(action.actuatorType().name(), action.command(), action.commandValue(),
                                callerService));
            } catch (FeignException.NotFound e) {
                throw new ActuatorNotFoundException(locationId, action.actuatorType().name());
            }
        }
    }
}
