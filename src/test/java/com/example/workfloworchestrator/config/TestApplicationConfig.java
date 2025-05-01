package com.example.workfloworchestrator.config;

import com.example.workfloworchestrator.engine.executor.*;
import com.example.workfloworchestrator.messaging.RabbitMQReceiver;
import com.example.workfloworchestrator.messaging.RabbitMQSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * Main test configuration that pulls together all test-specific beans
 */
@TestConfiguration
@Profile("test")
@Import({TestRabbitConfig.class})
public class TestApplicationConfig {

    @Bean
    @Primary
    public Map<String, TaskExecutor> testTaskExecutors() {
        Map<String, TaskExecutor> executors = new HashMap<>();

        // Add test executors
        executors.put("test-executor", new TestTaskExecutor());
        executors.put("failing-executor", new FailingTaskExecutor());
        executors.put("retry-test-executor", new RetryTestTaskExecutor());
        executors.put("slow-executor", new SlowTaskExecutor());

        return executors;
    }

    @Bean
    @Primary
    public RabbitMQSender mockRabbitMQSender() {
        return mock(RabbitMQSender.class);
    }

    @Bean
    @Primary
    public RabbitMQReceiver mockRabbitMQReceiver() {
        return mock(RabbitMQReceiver.class);
    }
}
