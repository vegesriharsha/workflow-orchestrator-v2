package com.example.workfloworchestrator.engine.strategy;

import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SequentialExecutionStrategyTest {

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private WorkflowExecutionService workflowExecutionService;

    private SequentialExecutionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SequentialExecutionStrategy(taskExecutionService, workflowExecutionService);
    }

    @Test
    void execute_WithEmptyTasks_ShouldCompleteSuccessfully() {
        // Arrange
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setTasks(new ArrayList<>());

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowDefinition(definition);

        // Act
        CompletableFuture<WorkflowStatus> future = strategy.execute(execution);

        // Assert
        assertThat(future.join()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void execute_WithSuccessfulTasks_ShouldCompleteWorkflow() {
        // Arrange
        TaskDefinition task1 = createTaskDefinition("task1", 0);
        TaskDefinition task2 = createTaskDefinition("task2", 1);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.getTasks().add(task1);
        definition.getTasks().add(task2);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowDefinition(definition);
        execution.setVariables(new HashMap<>());

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(execution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(execution), eq(task2), anyMap()))
                .thenReturn(taskExec2);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));

        // Act
        CompletableFuture<WorkflowStatus> future = strategy.execute(execution);

        // Assert
        assertThat(future.join()).isEqualTo(WorkflowStatus.COMPLETED);
        verify(taskExecutionService, times(2)).executeTask(any());
    }

    @Test
    void execute_WithFailedTask_ShouldFailWorkflow() {
        // Arrange
        TaskDefinition task = createTaskDefinition("failing-task", 0);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.getTasks().add(task);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowDefinition(definition);
        execution.setVariables(new HashMap<>());

        TaskExecution failedTaskExec = createTaskExecution(task, TaskStatus.FAILED);
        failedTaskExec.setErrorMessage("Task failed");

        when(taskExecutionService.createTaskExecution(eq(execution), eq(task), anyMap()))
                .thenReturn(failedTaskExec);

        when(taskExecutionService.executeTask(failedTaskExec.getId()))
                .thenReturn(CompletableFuture.completedFuture(failedTaskExec));

        // Act
        CompletableFuture<WorkflowStatus> future = strategy.execute(execution);

        // Assert
        assertThat(future.join()).isEqualTo(WorkflowStatus.FAILED);
        verify(workflowExecutionService, times (2)).save(any());
    }

    @Test
    void execute_WithUserReviewTask_ShouldPauseForReview() {
        // Arrange
        TaskDefinition reviewTask = createTaskDefinition("review-task", 0);
        reviewTask.setRequireUserReview(true);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.getTasks().add(reviewTask);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflowDefinition(definition);
        execution.setVariables(new HashMap<>());

        TaskExecution taskExec = createTaskExecution(reviewTask, TaskStatus.PENDING);

        when(taskExecutionService.createTaskExecution(eq(execution), eq(reviewTask), anyMap()))
                .thenReturn(taskExec);

        // Act
        CompletableFuture<WorkflowStatus> future = strategy.execute(execution);

        // Assert
        assertThat(future.join()).isEqualTo(WorkflowStatus.AWAITING_USER_REVIEW);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.AWAITING_USER_REVIEW);
        verify(taskExecutionService).createUserReviewPoint(taskExec.getId());
        verify(taskExecutionService, never()).executeTask(any());
    }

    @Test
    void executeSubset_ShouldExecuteOnlySpecifiedTasks() {
        // Arrange
        TaskDefinition task1 = createTaskDefinition("task1", 0);
        task1.setId(1L);
        TaskDefinition task2 = createTaskDefinition("task2", 1);
        task2.setId(2L);
        TaskDefinition task3 = createTaskDefinition("task3", 2);
        task3.setId(3L);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.getTasks().add(task1);
        definition.getTasks().add(task2);
        definition.getTasks().add(task3);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowDefinition(definition);
        execution.setVariables(new HashMap<>());

        List<Long> subsetIds = List.of(1L, 3L); // Only task1 and task3

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution taskExec3 = createTaskExecution(task3, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(execution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(execution), eq(task3), anyMap()))
                .thenReturn(taskExec3);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec3.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec3));

        // Act
        CompletableFuture<WorkflowStatus> future = strategy.executeSubset(execution, subsetIds);

        // Assert
        assertThat(future.join()).isEqualTo(WorkflowStatus.COMPLETED);
        verify(taskExecutionService, times(2)).executeTask(any());
        verify(taskExecutionService, never()).createTaskExecution(eq(execution), eq(task2), anyMap());
    }

    private TaskDefinition createTaskDefinition(String name, int order) {
        TaskDefinition task = new TaskDefinition();
        task.setName(name);
        task.setExecutionOrder(order);
        task.setType("test");
        return task;
    }

    private TaskExecution createTaskExecution(TaskDefinition definition, TaskStatus status) {
        TaskExecution execution = new TaskExecution();
        execution.setId(System.currentTimeMillis());
        execution.setTaskDefinition(definition);
        execution.setStatus(status);
        execution.setOutputs(new HashMap<>());
        return execution;
    }
}
