package com.example.workfloworchestrator.config;

import com.example.workfloworchestrator.engine.executor.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;

/**
 * Test configuration for workflow orchestrator
 * Provides mock executors and test-specific beans
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public Map<String, TaskExecutor> testTaskExecutors(
            TestTaskExecutor testTaskExecutor,
            FailingTaskExecutor failingTaskExecutor,
            RetryTestTaskExecutor retryTestTaskExecutor,
            SlowTaskExecutor slowTaskExecutor) {

        Map<String, TaskExecutor> executors = new HashMap<>();
        executors.put(testTaskExecutor.getTaskType(), testTaskExecutor);
        executors.put(failingTaskExecutor.getTaskType(), failingTaskExecutor);
        executors.put(retryTestTaskExecutor.getTaskType(), retryTestTaskExecutor);
        executors.put(slowTaskExecutor.getTaskType(), slowTaskExecutor);

        return executors;
    }
}

