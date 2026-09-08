package com.insighton.ai.domain.suggestion.event;

import com.insighton.ai.common.config.RabbitConfig;
import com.insighton.ai.domain.suggestion.batch.SuggestionGenerationScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SuggestionActionEventListener {

    private final SuggestionGenerationScheduler suggestionGenerationScheduler;

    @RabbitListener(queues = RabbitConfig.SUGGESTION_ACTION_QUEUE)
    public void handleSuggestionAction(AiSuggestionActionEvent event) {

        log.info("Rule Engine AI제안 이벤트 수신 - locationId: {}, metricKey:{}, value:{}", event.locationId(),
                event.metricKey(), event.value());

        suggestionGenerationScheduler.generateEventTriggeredSuggestion(event);
    }
}
