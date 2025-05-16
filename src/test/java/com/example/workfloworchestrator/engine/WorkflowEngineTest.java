package com.example.workfloworchestrator.engine;

import com.example.workfloworchestrator.engine.strategy.ExecutionStrategy;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.EventPublisherService;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock
    private WorkflowExecutionService workflowExecutionService;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private EventPublisherService eventPublisherService;

    @Mock
    private ExecutionStrategy mockStrategy;

    private WorkflowEngine workflowEngine;
    private Map<WorkflowDefinition.ExecutionStrategyType, ExecutionStrategy> executionStrategies;

    @BeforeEach
    void setUp() {
        executionStrategies = Map.of(
                WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL, mockStrategy
        );
        workflowEngine = new WorkflowEngine(
                workflowExecutionService,
                taskExecutionService,
                eventPublisherService,
                executionStrategies
        );
    }

    @Test
    void executeWorkflow_ShouldExecuteSuccessfully() {
        // Arrange
        Long workflowExecutionId = 1L;
        WorkflowExecution mockExecution = createMockWorkflowExecution();

        when(workflowExecutionService.getWorkflowExecution(workflowExecutionId))
                .thenReturn(mockExecution);

        when(mockStrategy.execute(any(WorkflowExecution.class)))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));

        // Act
        workflowEngine.executeWorkflow(workflowExecutionId);

        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                workflowExecutionId, WorkflowStatus.RUNNING);
        verify(eventPublisherService).publishWorkflowStartedEvent(mockExecution);
    }

    @Test
    void restartTask_ShouldRestartTaskSuccessfully() {
        // Arrange
        Long workflowExecutionId = 1L;
        Long taskExecutionId = 2L;

        WorkflowExecution mockWorkflowExecution = createMockWorkflowExecution();
        TaskExecution mockTaskExecution = createMockTaskExecution();

        when(workflowExecutionService.getWorkflowExecution(workflowExecutionId))
                .thenReturn(mockWorkflowExecution);
        when(taskExecutionService.getTaskExecution(taskExecutionId))
                .thenReturn(mockTaskExecution);
        when(taskExecutionService.getTaskExecutionsForWorkflow(workflowExecutionId))
                .thenReturn(List.of(mockTaskExecution));

        // Act
        workflowEngine.restartTask(workflowExecutionId, taskExecutionId);

        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                workflowExecutionId, WorkflowStatus.RUNNING);
        verify(taskExecutionService).saveTaskExecution(mockTaskExecution);
    }

    @Test
    void executeTaskSubset_ShouldExecuteSubsetSuccessfully() {
        // Arrange
        Long workflowExecutionId = 1L;
        List<Long> taskIds = List.of(2L, 3L);

        WorkflowExecution mockExecution = createMockWorkflowExecution();

        when(workflowExecutionService.getWorkflowExecution(workflowExecutionId))
                .thenReturn(mockExecution);
        when(mockStrategy.executeSubset(any(WorkflowExecution.class), anyList()))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));

        // Act
        workflowEngine.executeTaskSubset(workflowExecutionId, taskIds);

        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                workflowExecutionId, WorkflowStatus.RUNNING);
        verify(mockStrategy).executeSubset(mockExecution, taskIds);
    }

    @Test
    void executeWorkflow_WhenWorkflowFails_ShouldHandleError() {
        // Arrange
        Long workflowExecutionId = 1L;
        WorkflowExecution mockExecution = createMockWorkflowExecution();

        when(workflowExecutionService.getWorkflowExecution(workflowExecutionId))
                .thenReturn(mockExecution);
        when(mockStrategy.execute(any(WorkflowExecution.class)))
                .thenThrow(new RuntimeException("Test error"));

        // Act
        workflowEngine.executeWorkflow(workflowExecutionId);

        // Assert
        verify(workflowExecutionService).updateWorkflowExecutionStatus(
                workflowExecutionId, WorkflowStatus.FAILED);
        verify(eventPublisherService).publishWorkflowFailedEvent(mockExecution);
    }

    @Test
    void executeWorkflow_WhenAlreadyCompleted_ShouldNotExecute() {
        // Arrange
        Long workflowExecutionId = 1L;
        WorkflowExecution completedExecution = createMockWorkflowExecution();
        completedExecution.setStatus(WorkflowStatus.COMPLETED);

        when(workflowExecutionService.getWorkflowExecution(workflowExecutionId))
                .thenReturn(completedExecution);

        // Act
        workflowEngine.executeWorkflow(workflowExecutionId);

        // Assert
        verify(mockStrategy, never()).execute(any());
        verify(workflowExecutionService, never()).updateWorkflowExecutionStatus(anyLong(), any());
    }

    private WorkflowExecution createMockWorkflowExecution() {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setStatus(WorkflowStatus.CREATED);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        execution.setWorkflowDefinition(definition);

        return execution;
    }

    private TaskExecution createMockTaskExecution() {
        TaskExecution taskExecution = new TaskExecution();
        taskExecution.setId(2L);
        taskExecution.setStatus(TaskStatus.PENDING);

        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setId(1L);
        taskExecution.setTaskDefinition(taskDefinition);

        return taskExecution;
    }
}
