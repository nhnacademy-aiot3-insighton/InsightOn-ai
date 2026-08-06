package com.insighton.ai.config;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RabbitConfig {

    public static final String CORE_EVENTS_EXCHANGE = "insighton.core-events";
    public static final String GROUP_DELETED_QUEUE = "ai-service.group-deleted.queue";
    public static final String LOCATION_DELETED_QUEUE = "ai-service.location-deleted.queue";
    public static final String GROUP_DELETED_ROUTING_KEY = "group.deleted";
    public static final String LOCATION_DELETED_ROUTING_KEY = "location.deleted";

    public static final String NOTIFICATION_FANOUT_EXCHANGE = "insighton.dashboard-notification-fanout";

    public static final String RULE_ENGINE_EVENTS_EXCHANGE = "insighton.rule-engine-events";
    public static final String SUGGESTION_ACTION_QUEUE = "ai-service.suggestion-action.queue";
    public static final String SUGGESTION_ACTION_ROUTING_KEY = "ai.suggestion.action";

    @Bean
    public TopicExchange coreEventsExchange() {
        return new TopicExchange(CORE_EVENTS_EXCHANGE);
    }

    @Bean
    public Queue groupDeletedQueue() {
        return new Queue(GROUP_DELETED_QUEUE, true);
    }

    @Bean
    public Queue locationDeletedQueue() {
        return new Queue(LOCATION_DELETED_QUEUE, true);
    }

    @Bean
    public Binding groupDeletedBinding(Queue groupDeletedQueue,
                                       @Qualifier("coreEventsExchange") TopicExchange coreEventExchange) {
        return BindingBuilder.bind(groupDeletedQueue)
                .to(coreEventExchange)
                .with(GROUP_DELETED_ROUTING_KEY);
    }

    @Bean
    public Binding locationDeletedBinding(Queue locationDeletedQueue,
                                          @Qualifier("coreEventsExchange") TopicExchange coreEventExchange) {
        return BindingBuilder.bind(locationDeletedQueue)
                .to(coreEventExchange)
                .with(LOCATION_DELETED_ROUTING_KEY);
    }

    @Bean
    public TopicExchange ruleEngineEventExchange() {
        return new TopicExchange(RULE_ENGINE_EVENTS_EXCHANGE);
    }

    @Bean
    public Queue suggestionActionQueue() {
        return new Queue(SUGGESTION_ACTION_QUEUE, true);
    }

    @Bean
    public Binding suggestionActionBinding(Queue suggestionActionQueue,
                                           @Qualifier("ruleEngineEventExchange") TopicExchange ruleEngineEventExchange) {
        return BindingBuilder.bind(suggestionActionQueue)
                .to(ruleEngineEventExchange)
                .with(SUGGESTION_ACTION_ROUTING_KEY);
    }

    @Bean
    public MessageRecoverer messageRecoverer() {
        return (message, cause) -> {
            String queue = message.getMessageProperties().getConsumerQueue();
            String body = new String(message.getBody(), StandardCharsets.UTF_8);

            switch (Objects.requireNonNull(queue)) {
                case SUGGESTION_ACTION_QUEUE -> log.error("AI제안 이벤트 재시도 소진 - queue: {}, body: {}", queue, body, cause);

                case GROUP_DELETED_QUEUE, LOCATION_DELETED_QUEUE ->
                        log.error("Core 라이프사이클 이벤트 재시도 소진 - queue: {}, body: {}", queue, body, cause);

                default -> log.error("RabbitMQ 재시도소진 - queue: {}, body: {}", queue, body, cause);
            }
        };
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
