package com.chatbot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitMQConfig {

    public static final String FAILED_EXCHANGE = "movie.sync.failed.exchange";
    public static final String FAILED_QUEUE = "movie.sync.failed.queue";
    public static final String FAILED_ROUTING_KEY = "failed.batch";

    public static final String DELAY_EXCHANGE = "movie.sync.retry.delay.exchange";
    public static final String DELAY_QUEUE = "movie.sync.retry.delay.queue";
    public static final String DELAY_ROUTING_KEY = "retry.batch";

    public static final String PARKING_QUEUE = "movie.sync.failed.parking.queue";
    public static final String PARKING_ROUTING_KEY = "failed.parking";

    @Value("${sync.retry-delay-ms:300000}")
    private long retryDelayMs;

    @Bean
    public DirectExchange failedBatchExchange() {
        return new DirectExchange(FAILED_EXCHANGE);
    }

    @Bean
    public DirectExchange retryDelayExchange() {
        return new DirectExchange(DELAY_EXCHANGE);
    }

    @Bean
    public Queue failedBatchQueue() {
        return QueueBuilder.durable(FAILED_QUEUE).build();
    }

    @Bean
    public Queue retryDelayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .withArgument("x-message-ttl", retryDelayMs)
                .withArgument("x-dead-letter-exchange", FAILED_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FAILED_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding failedBinding(Queue failedBatchQueue, DirectExchange failedBatchExchange) {
        return BindingBuilder.bind(failedBatchQueue).to(failedBatchExchange).with(FAILED_ROUTING_KEY);
    }

    @Bean
    public Binding retryDelayBinding(Queue retryDelayQueue, DirectExchange retryDelayExchange) {
        return BindingBuilder.bind(retryDelayQueue).to(retryDelayExchange).with(DELAY_ROUTING_KEY);
    }

    @Bean
    public Queue parkingQueue() {
        return QueueBuilder.durable(PARKING_QUEUE).build();
    }

    @Bean
    public Binding parkingBinding(Queue parkingQueue, DirectExchange failedBatchExchange) {
        return BindingBuilder.bind(parkingQueue).to(failedBatchExchange).with(PARKING_ROUTING_KEY);
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        JsonMapper mapper = JsonMapper.builder().build();
        return new JacksonJsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        return factory;
    }
}
