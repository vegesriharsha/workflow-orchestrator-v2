package com.example.workfloworchestrator.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.mock;

/**
 * Test configuration for RabbitMQ components
 * Provides mock beans to avoid actual RabbitMQ connections in tests
 */
@TestConfiguration
@Profile("test")
public class TestRabbitConfig {

    @MockBean
    private ConnectionFactory connectionFactory;

    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate() {
        return mock(RabbitTemplate.class);
    }

    @Bean
    public Queue taskQueue() {
        return new Queue("test.task.queue", false);
    }

    @Bean
    public Queue resultQueue() {
        return new Queue("test.result.queue", false);
    }
}
