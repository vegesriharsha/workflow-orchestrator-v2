package com.example.workfloworchestrator.config;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.engine.executor.TaskExecutor;
import com.example.workfloworchestrator.engine.strategy.ExecutionStrategy;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.service.EventPublisherService;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * Test configuration to prevent NPE in tests by providing properly mocked beans
 */
@TestConfiguration
public class TestConfig {

    // Mock all the required services to prevent NPE
    @MockBean
    private WorkflowExecutionService workflowExecutionService;

    @MockBean
    private TaskExecutionService taskExecutionService;

    @MockBean
    private EventPublisherService eventPublisherService;

    /**
     * Provide mock execution strategies to prevent NPE
     */
    @Bean
    @Primary
    public Map<WorkflowDefinition.ExecutionStrategyType, ExecutionStrategy> executionStrategies() {
        Map<WorkflowDefinition.ExecutionStrategyType, ExecutionStrategy> strategies = new HashMap<>();

        // Mock all strategy types
        ExecutionStrategy mockSequentialStrategy = mock(ExecutionStrategy.class);
        ExecutionStrategy mockParallelStrategy = mock(ExecutionStrategy.class);
        ExecutionStrategy mockConditionalStrategy = mock(ExecutionStrategy.class);

        strategies.put(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL, mockSequentialStrategy);
        strategies.put(WorkflowDefinition.ExecutionStrategyType.PARALLEL, mockParallelStrategy);
        strategies.put(WorkflowDefinition.ExecutionStrategyType.CONDITIONAL, mockConditionalStrategy);

        return strategies;
    }

    /**
     * Provide mock task executors to prevent NPE
     */
    @Bean
    @Primary
    public Map<String, TaskExecutor> taskExecutors() {
        Map<String, TaskExecutor> executors = new HashMap<>();

        // Mock common task executor types
        TaskExecutor mockHttpExecutor = mock(TaskExecutor.class);
        TaskExecutor mockRabbitMqExecutor = mock(TaskExecutor.class);
        TaskExecutor mockCustomExecutor = mock(TaskExecutor.class);

        executors.put("http", mockHttpExecutor);
        executors.put("rest-api", mockHttpExecutor);
        executors.put("rabbitmq", mockRabbitMqExecutor);
        executors.put("custom", mockCustomExecutor);

        return executors;
    }

    /**
     * Provide a fully configured WorkflowEngine bean for tests
     */
    @Bean
    @Primary
    public WorkflowEngine workflowEngine(
            WorkflowExecutionService workflowExecutionService,
            TaskExecutionService taskExecutionService,
            EventPublisherService eventPublisherService,
            Map<WorkflowDefinition.ExecutionStrategyType, ExecutionStrategy> executionStrategies) {

        return new WorkflowEngine(
                workflowExecutionService,
                taskExecutionService,
                eventPublisherService,
                executionStrategies
        );
    }
}
