package com.onlinejudge.common.reliability;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(name = "onlinejudge.reliability.rabbitmq.enabled", havingValue = "true")
public class RabbitMqReliabilityConfiguration {
    public static final String EVENTS_EXCHANGE = "onlinejudge.events.v2";
    public static final String DLX_EXCHANGE = "onlinejudge.events.dlx.v2";
    public static final String LEARNING_QUEUE = "onlinejudge.learning.events.v2";
    public static final String LEARNING_RETRY_QUEUE = "onlinejudge.learning.retry.v2";
    public static final String LEARNING_DLQ = "onlinejudge.learning.dlq.v2";
    public static final String HOMEWORK_PUBLISHED_ROUTING_KEY = "onlinejudge.assessment.homework.published.v2";

    @Bean
    TopicExchange onlinejudgeEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange onlinejudgeDeadLetterExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    Queue learningEventsQueue() {
        return QueueBuilder.durable(LEARNING_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey("learning")
                .build();
    }

    @Bean
    Queue learningRetryQueue() {
        return QueueBuilder.durable(LEARNING_RETRY_QUEUE)
                .ttl(1_000)
                .deadLetterExchange(EVENTS_EXCHANGE)
                .deadLetterRoutingKey(HOMEWORK_PUBLISHED_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue learningDeadLetterQueue() {
        return QueueBuilder.durable(LEARNING_DLQ).build();
    }

    @Bean
    Binding homeworkPublishedToLearning(TopicExchange onlinejudgeEventsExchange, Queue learningEventsQueue) {
        return BindingBuilder.bind(learningEventsQueue)
                .to(onlinejudgeEventsExchange)
                .with(HOMEWORK_PUBLISHED_ROUTING_KEY);
    }

    @Bean
    Binding learningDeadLetterBinding(TopicExchange onlinejudgeDeadLetterExchange, Queue learningDeadLetterQueue) {
        return BindingBuilder.bind(learningDeadLetterQueue).to(onlinejudgeDeadLetterExchange).with("learning");
    }

    @Bean
    @Primary
    RabbitTemplate reliableRabbitTemplate(CachingConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    Jackson2JsonMessageConverter reliableEventMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setCreateMessageIds(false);
        return converter;
    }
}
