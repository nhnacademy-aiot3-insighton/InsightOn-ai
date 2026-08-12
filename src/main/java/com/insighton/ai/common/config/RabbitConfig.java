package com.insighton.ai.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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
    public static final String ALERT_ACTION_QUEUE = "ai-service.alert-action.queue";
    public static final String ALERT_ACTION_ROUTING_KEY = "ai.alert.action";

    public static final String RULE_ENGINE_DEAD_LETTER_EXCHANGE = "insighton.rule-engine-events.dlx";
    public static final String ALERT_ACTION_DLQ = "ai-service.alert-action.dlq";

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
    public Queue engineAlertActionQueue() {
        return QueueBuilder.durable(ALERT_ACTION_QUEUE)
                .withArgument("x-dead-letter-exchange", RULE_ENGINE_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ALERT_ACTION_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding engineAlertActionBinding(Queue engineAlertActionQueue,
                                            @Qualifier("ruleEngineEventExchange") TopicExchange ruleEngineEventExchange) {
        return BindingBuilder.bind(engineAlertActionQueue)
                .to(ruleEngineEventExchange)
                .with(ALERT_ACTION_ROUTING_KEY);
    }

    @Bean
    public TopicExchange ruleEngineDeadLetterExchange() {
        return new TopicExchange(RULE_ENGINE_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue alertActionDeadLetterQueue() {
        return new Queue(ALERT_ACTION_DLQ, true);
    }

    @Bean
    public Binding alertActionDeadLetterBinding(Queue alertActionDeadLetterQueue,
                                                @Qualifier("ruleEngineDeadLetterExchange") TopicExchange ruleEngineDeadLetterExchange) {
        return BindingBuilder.bind(alertActionDeadLetterQueue)
                .to(ruleEngineDeadLetterExchange)
                .with(ALERT_ACTION_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
