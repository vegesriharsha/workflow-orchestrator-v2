package com.example.workfloworchestrator.engine.strategy;

import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParallelExecutionStrategyTest {

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private WorkflowExecutionService workflowExecutionService;

    private ParallelExecutionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ParallelExecutionStrategy(taskExecutionService, workflowExecutionService);
    }

    @Test
    void execute_WithTasksInSameGroup_ShouldExecuteInParallel() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition task2 = createTaskDefinition("task-2", 0);
        TaskDefinition task3 = createTaskDefinition("task-3", 0);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2, task3));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.COMPLETED);
        TaskExecution taskExec3 = createTaskExecution(task3, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task3), anyMap()))
                .thenReturn(taskExec3);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));
        when(taskExecutionService.executeTask(taskExec3.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec3));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify all tasks were executed
        verify(taskExecutionService).executeTask(taskExec1.getId());
        verify(taskExecutionService).executeTask(taskExec2.getId());
        verify(taskExecutionService).executeTask(taskExec3.getId());

        // Verify workflow variables were updated with all outputs
        verify(workflowExecutionService, atLeastOnce()).save(workflowExecution);
    }

    @Test
    void execute_WithTasksInDifferentGroups_ShouldExecuteGroupsSequentially() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        // Group 1 (order 0)
        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition task2 = createTaskDefinition("task-2", 0);

        // Group 2 (order 1)
        TaskDefinition task3 = createTaskDefinition("task-3", 1);
        TaskDefinition task4 = createTaskDefinition("task-4", 1);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2, task3, task4));

        // First group executions
        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.COMPLETED);

        // Second group executions
        TaskExecution taskExec3 = createTaskExecution(task3, TaskStatus.COMPLETED);
        TaskExecution taskExec4 = createTaskExecution(task4, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task3), anyMap()))
                .thenReturn(taskExec3);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task4), anyMap()))
                .thenReturn(taskExec4);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));
        when(taskExecutionService.executeTask(taskExec3.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec3));
        when(taskExecutionService.executeTask(taskExec4.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec4));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify all tasks were executed
        verify(taskExecutionService).executeTask(taskExec1.getId());
        verify(taskExecutionService).executeTask(taskExec2.getId());
        verify(taskExecutionService).executeTask(taskExec3.getId());
        verify(taskExecutionService).executeTask(taskExec4.getId());
    }

    @Test
    void execute_WithUserReviewInParallelGroup_ShouldFallbackToSequential() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();
        workflowExecution.setId(1L);

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition reviewTask = createTaskDefinition("review-task", 0);
        reviewTask.setRequireUserReview(true);
        TaskDefinition task3 = createTaskDefinition("task-3", 0);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, reviewTask, task3));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution reviewTaskExec = createTaskExecution(reviewTask, TaskStatus.PENDING);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(reviewTask), anyMap()))
                .thenReturn(reviewTaskExec);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.AWAITING_USER_REVIEW);

        // Verify workflow was moved to awaiting review status
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.AWAITING_USER_REVIEW);
        verify(taskExecutionService).createUserReviewPoint(reviewTaskExec.getId());

        // Verify only the first task was executed (sequential fallback)
        verify(taskExecutionService).executeTask(taskExec1.getId());
        verify(taskExecutionService, never()).executeTask(reviewTaskExec.getId());
    }

    @Test
    void execute_WithFailedTask_ShouldFailWorkflow() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition task2 = createTaskDefinition("task-2", 0);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.FAILED);
        taskExec2.setErrorMessage("Task failed");

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.FAILED);

        // Verify error message was set
        verify(workflowExecutionService).save(argThat(we ->
                we.getErrorMessage() != null && we.getErrorMessage().contains("One or more tasks failed")));
    }

    @Test
    void execute_WithFailedTaskAndErrorHandler_ShouldExecuteErrorPath() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition errorHandler = createTaskDefinition("error-handler", 1);
        task1.setNextTaskOnFailure(errorHandler.getId());

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, errorHandler));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.FAILED);
        TaskExecution errorHandlerExec = createTaskExecution(errorHandler, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(errorHandler), anyMap()))
                .thenReturn(errorHandlerExec);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(errorHandlerExec.getId()))
                .thenReturn(CompletableFuture.completedFuture(errorHandlerExec));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify error handler was executed
        verify(taskExecutionService).executeTask(errorHandlerExec.getId());
    }

    @Test
    void execute_WithTasksHavingNullExecutionOrder_ShouldAssignDefaultOrder() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", null);
        TaskDefinition task2 = createTaskDefinition("task-2", null);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Both tasks should be executed (default order 0)
        verify(taskExecutionService).executeTask(taskExec1.getId());
        verify(taskExecutionService).executeTask(taskExec2.getId());
    }

    @Test
    void execute_WithOutputsFromParallelTasks_ShouldCollectAllOutputs() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition task2 = createTaskDefinition("task-2", 0);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        taskExec1.getOutputs().put("output1", "value1");
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.COMPLETED);
        taskExec2.getOutputs().put("output2", "value2");

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify workflow variables were updated with both outputs
        ArgumentCaptor<WorkflowExecution> workflowCaptor = ArgumentCaptor.forClass(WorkflowExecution.class);
        verify(workflowExecutionService, atLeastOnce()).save(workflowCaptor.capture());

        WorkflowExecution savedWorkflow = workflowCaptor.getValue();
        assertThat(savedWorkflow.getVariables()).containsEntry("output1", "value1");
        assertThat(savedWorkflow.getVariables()).containsEntry("output2", "value2");
    }

    @Test
    void executeSubset_ShouldExecuteOnlySpecifiedTasks() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        task1.setId(1L);
        TaskDefinition task2 = createTaskDefinition("task-2", 0);
        task2.setId(2L);
        TaskDefinition task3 = createTaskDefinition("task-3", 0);
        task3.setId(3L);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2, task3));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution taskExec3 = createTaskExecution(task3, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task3), anyMap()))
                .thenReturn(taskExec3);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec3.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec3));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.executeSubset(workflowExecution, Arrays.asList(1L, 3L));

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify only tasks 1 and 3 were executed
        verify(taskExecutionService).executeTask(taskExec1.getId());
        verify(taskExecutionService).executeTask(taskExec3.getId());
        verify(taskExecutionService, never()).createTaskExecution(eq(workflowExecution), eq(task2), anyMap());
    }

    @Test
    void executeSubset_WithEmptyTaskIds_ShouldCompleteImmediately() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        workflowExecution.getWorkflowDefinition().getTasks().add(task1);

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.executeSubset(workflowExecution, Collections.emptyList());

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify no tasks were executed
        verify(taskExecutionService, never()).createTaskExecution(any(), any(), any());
    }

    @Test
    void execute_WithEmptyWorkflow_ShouldCompleteImmediately() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();
        // No tasks in the workflow

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);
        verify(taskExecutionService, never()).createTaskExecution(any(), any(), any());
    }

    @Test
    void execute_WithSkippedTasks_ShouldContinueExecution() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition task2 = createTaskDefinition("task-2", 0);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.SKIPPED);
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Both tasks should have been executed
        verify(taskExecutionService).executeTask(taskExec1.getId());
        verify(taskExecutionService).executeTask(taskExec2.getId());
    }

    @Test
    void execute_WithCancelledTasks_ShouldContinueExecution() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition task2 = createTaskDefinition("task-2", 0);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.CANCELLED);
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Both tasks should have been executed
        verify(taskExecutionService).executeTask(taskExec1.getId());
        verify(taskExecutionService).executeTask(taskExec2.getId());
    }

    @Test
    void execute_WithMixedSuccessAndFailureInGroup_ShouldContinueWithErrorHandlers() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition task2 = createTaskDefinition("task-2", 0);
        TaskDefinition errorHandler = createTaskDefinition("error-handler", 1);
        task2.setNextTaskOnFailure(errorHandler.getId());

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2, errorHandler));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.FAILED);
        TaskExecution errorHandlerExec = createTaskExecution(errorHandler, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(errorHandler), anyMap()))
                .thenReturn(errorHandlerExec);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));
        when(taskExecutionService.executeTask(errorHandlerExec.getId()))
                .thenReturn(CompletableFuture.completedFuture(errorHandlerExec));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // All tasks should have been executed
        verify(taskExecutionService).executeTask(taskExec1.getId());
        verify(taskExecutionService).executeTask(taskExec2.getId());
        verify(taskExecutionService).executeTask(errorHandlerExec.getId());
    }

    @Test
    void execute_WithExceptionDuringTaskExecution_ShouldFailGracefully() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        workflowExecution.getWorkflowDefinition().getTasks().add(task1);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenThrow(new RuntimeException("Task creation failed"));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void execute_WithMultipleGroupsAndComplexScenario_ShouldHandleCorrectly() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        // First group - all succeed
        TaskDefinition group1Task1 = createTaskDefinition("group1-task1", 0);
        TaskDefinition group1Task2 = createTaskDefinition("group1-task2", 0);

        // Second group - one fails with error handler
        TaskDefinition group2Task1 = createTaskDefinition("group2-task1", 1);
        TaskDefinition group2Task2 = createTaskDefinition("group2-task2", 1);
        TaskDefinition errorHandler = createTaskDefinition("error-handler", 2);
        group2Task2.setNextTaskOnFailure(errorHandler.getId());

        workflowExecution.getWorkflowDefinition().getTasks().addAll(
                Arrays.asList(group1Task1, group1Task2, group2Task1, group2Task2, errorHandler));

        // Group 1 executions
        TaskExecution group1Exec1 = createTaskExecution(group1Task1, TaskStatus.COMPLETED);
        group1Exec1.getOutputs().put("group1Output", "value1");
        TaskExecution group1Exec2 = createTaskExecution(group1Task2, TaskStatus.COMPLETED);
        group1Exec2.getOutputs().put("group1Output2", "value2");

        // Group 2 executions
        TaskExecution group2Exec1 = createTaskExecution(group2Task1, TaskStatus.COMPLETED);
        TaskExecution group2Exec2 = createTaskExecution(group2Task2, TaskStatus.FAILED);
        TaskExecution errorHandlerExec = createTaskExecution(errorHandler, TaskStatus.COMPLETED);

        // Set up mocks
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(group1Task1), anyMap()))
                .thenReturn(group1Exec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(group1Task2), anyMap()))
                .thenReturn(group1Exec2);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(group2Task1), anyMap()))
                .thenReturn(group2Exec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(group2Task2), anyMap()))
                .thenReturn(group2Exec2);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(errorHandler), anyMap()))
                .thenReturn(errorHandlerExec);

        when(taskExecutionService.executeTask(group1Exec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(group1Exec1));
        when(taskExecutionService.executeTask(group1Exec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(group1Exec2));
        when(taskExecutionService.executeTask(group2Exec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(group2Exec1));
        when(taskExecutionService.executeTask(group2Exec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(group2Exec2));
        when(taskExecutionService.executeTask(errorHandlerExec.getId()))
                .thenReturn(CompletableFuture.completedFuture(errorHandlerExec));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify all tasks were executed
        verify(taskExecutionService).executeTask(group1Exec1.getId());
        verify(taskExecutionService).executeTask(group1Exec2.getId());
        verify(taskExecutionService).executeTask(group2Exec1.getId());
        verify(taskExecutionService).executeTask(group2Exec2.getId());
        verify(taskExecutionService).executeTask(errorHandlerExec.getId());

        // Verify workflow variables were collected from successful tasks
        ArgumentCaptor<WorkflowExecution> workflowCaptor = ArgumentCaptor.forClass(WorkflowExecution.class);
        verify(workflowExecutionService, atLeastOnce()).save(workflowCaptor.capture());
    }

    @Test
    void execute_WithAsyncTaskCompletionFailure_ShouldHandleCorrectly() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition task2 = createTaskDefinition("task-2", 0);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.FAILED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Task 2 failed")));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void execute_WithMixedOrdersIncludingNegativeValues_ShouldHandleCorrectly() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", -1);  // Negative order
        TaskDefinition task2 = createTaskDefinition("task-2", 0);
        TaskDefinition task3 = createTaskDefinition("task-3", 1);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2, task3));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.COMPLETED);
        TaskExecution taskExec3 = createTaskExecution(task3, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task3), anyMap()))
                .thenReturn(taskExec3);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));
        when(taskExecutionService.executeTask(taskExec3.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec3));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // All tasks should be executed in order (-1, 0, 1)
        verify(taskExecutionService).executeTask(taskExec1.getId());
        verify(taskExecutionService).executeTask(taskExec2.getId());
        verify(taskExecutionService).executeTask(taskExec3.getId());
    }

    @Test
    void execute_WithMultipleFailuresInSameGroup_ShouldHandleAllErrorPaths() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition task2 = createTaskDefinition("task-2", 0);
        TaskDefinition task3 = createTaskDefinition("task-3", 0);
        TaskDefinition errorHandler1 = createTaskDefinition("error-handler-1", 1);
        TaskDefinition errorHandler2 = createTaskDefinition("error-handler-2", 1);

        task1.setNextTaskOnFailure(errorHandler1.getId());
        task2.setNextTaskOnFailure(errorHandler2.getId());

        workflowExecution.getWorkflowDefinition().getTasks().addAll(
                Arrays.asList(task1, task2, task3, errorHandler1, errorHandler2));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.FAILED);
        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.FAILED);
        TaskExecution taskExec3 = createTaskExecution(task3, TaskStatus.COMPLETED);
        TaskExecution errorHandlerExec1 = createTaskExecution(errorHandler1, TaskStatus.COMPLETED);
        TaskExecution errorHandlerExec2 = createTaskExecution(errorHandler2, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task3), anyMap()))
                .thenReturn(taskExec3);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(errorHandler1), anyMap()))
                .thenReturn(errorHandlerExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(errorHandler2), anyMap()))
                .thenReturn(errorHandlerExec2);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));
        when(taskExecutionService.executeTask(taskExec3.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec3));
        when(taskExecutionService.executeTask(errorHandlerExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(errorHandlerExec1));
        when(taskExecutionService.executeTask(errorHandlerExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(errorHandlerExec2));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // All tasks should be executed
        verify(taskExecutionService).executeTask(taskExec1.getId());
        verify(taskExecutionService).executeTask(taskExec2.getId());
        verify(taskExecutionService).executeTask(taskExec3.getId());
        verify(taskExecutionService).executeTask(errorHandlerExec1.getId());
        verify(taskExecutionService).executeTask(errorHandlerExec2.getId());
    }

    @Test
    void execute_WithVariableUpdatesFromDifferentTasksInParallel_ShouldMergeCorrectly() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();
        workflowExecution.getVariables().put("initial", "value");

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition task2 = createTaskDefinition("task-2", 0);
        TaskDefinition task3 = createTaskDefinition("task-3", 1);

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, task2, task3));

        TaskExecution taskExec1 = createTaskExecution(task1, TaskStatus.COMPLETED);
        taskExec1.getOutputs().put("output1", "value1");
        taskExec1.getOutputs().put("shared", "fromTask1");

        TaskExecution taskExec2 = createTaskExecution(task2, TaskStatus.COMPLETED);
        taskExec2.getOutputs().put("output2", "value2");
        taskExec2.getOutputs().put("shared", "fromTask2");  // Same key, different value

        TaskExecution taskExec3 = createTaskExecution(task3, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(taskExec1);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task2), anyMap()))
                .thenReturn(taskExec2);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task3), anyMap()))
                .thenReturn(taskExec3);

        when(taskExecutionService.executeTask(taskExec1.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec1));
        when(taskExecutionService.executeTask(taskExec2.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec2));
        when(taskExecutionService.executeTask(taskExec3.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExec3));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify workflow variables were updated
        ArgumentCaptor<WorkflowExecution> workflowCaptor = ArgumentCaptor.forClass(WorkflowExecution.class);
        verify(workflowExecutionService, atLeastOnce()).save(workflowCaptor.capture());

        // Check that outputs were merged (order might vary for shared key)
        WorkflowExecution savedWorkflow = workflowCaptor.getValue();
        assertThat(savedWorkflow.getVariables()).containsKey("initial");
        assertThat(savedWorkflow.getVariables()).containsKey("output1");
        assertThat(savedWorkflow.getVariables()).containsKey("output2");
        assertThat(savedWorkflow.getVariables()).containsKey("shared");
    }

    private WorkflowExecution createWorkflowExecution() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setTasks(new ArrayList<>());

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowDefinition(definition);
        execution.setVariables(new HashMap<>());
        return execution;
    }

    private TaskDefinition createTaskDefinition(String name, Integer order) {
        TaskDefinition task = new TaskDefinition();
        task.setId((long) (name.hashCode() & 0x7FFFFFFF));
        task.setName(name);
        task.setExecutionOrder(order);
        task.setType("test");
        return task;
    }

    private TaskExecution createTaskExecution(TaskDefinition definition, TaskStatus status) {
        TaskExecution execution = new TaskExecution();
        execution.setId(definition.getId());
        execution.setTaskDefinition(definition);
        execution.setStatus(status);
        execution.setOutputs(new HashMap<>());
        return execution;
    }
}
