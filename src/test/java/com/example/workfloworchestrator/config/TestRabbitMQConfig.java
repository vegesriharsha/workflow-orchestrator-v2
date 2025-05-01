package com.example.workfloworchestrator.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for disabling RabbitMQ in tests
 */
@TestConfiguration
public class TestRabbitMQConfig {

    // Mock RabbitMQ-related beans to avoid actual connections
    @Bean
    @Primary
    public MockRabbitMQSender mockRabbitMQSender() {
        return new MockRabbitMQSender();
    }

    // Mock RabbitMQ sender for testing
    public static class MockRabbitMQSender {
        private final Map<String, Object> sentMessages = new HashMap<>();

        public void sendMessage(String exchange, String routingKey, Object message) {
            sentMessages.put(exchange + ":" + routingKey, message);
        }

        public Object getSentMessage(String exchange, String routingKey) {
            return sentMessages.get(exchange + ":" + routingKey);
        }

        public void reset() {
            sentMessages.clear();
        }
    }
}
