package com.example.workfloworchestrator.engine.scheduler;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.EventPublisherService;
import com.example.workfloworchestrator.service.TaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrySchedulerTest {

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private EventPublisherService eventPublisherService;

    @Mock
    private WorkflowEngine workflowEngine;

    private RetryScheduler retryScheduler;

    @BeforeEach
    void setUp() {
        retryScheduler = new RetryScheduler(
                taskExecutionService,
                eventPublisherService,
                workflowEngine
        );
    }

    @Test
    void retryFailedTasks_ShouldRetryTasksScheduledForRetry() {
        // Arrange
        TaskExecution taskExecution1 = createTaskForRetry(1L, 100L, 1);
        TaskExecution taskExecution2 = createTaskForRetry(2L, 200L, 2);
        List<TaskExecution> tasksToRetry = Arrays.asList(taskExecution1, taskExecution2);

        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(tasksToRetry);

        // Act
        retryScheduler.retryFailedTasks();

        // Assert
        verify(taskExecutionService).getTasksToRetry(any(LocalDateTime.class));

        // Verify task statuses were reset
        verify(taskExecutionService).saveTaskExecution(argThat(task ->
                task.getId().equals(1L) && task.getStatus() == TaskStatus.PENDING));
        verify(taskExecutionService).saveTaskExecution(argThat(task ->
                task.getId().equals(2L) && task.getStatus() == TaskStatus.PENDING));

        // Verify tasks were executed
        verify(taskExecutionService).executeTask(1L);
        verify(taskExecutionService).executeTask(2L);
    }

    @Test
    void retryFailedTasks_WhenRetryExecutionFails_ShouldContinueWithOtherTasks() {
        // Arrange
        TaskExecution taskExecution1 = createTaskForRetry(1L, 100L, 1);
        TaskExecution taskExecution2 = createTaskForRetry(2L, 200L, 2);
        List<TaskExecution> tasksToRetry = Arrays.asList(taskExecution1, taskExecution2);

        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(tasksToRetry);
        doThrow(new RuntimeException("Retry failed"))
                .when(taskExecutionService).executeTask(1L);

        // Act
        retryScheduler.retryFailedTasks();

        // Assert
        verify(taskExecutionService).executeTask(1L);
        verify(taskExecutionService).executeTask(2L); // Should still try second task

        // Verify retry tracker is updated for the failed workflow
        Map<Long, Integer> retryTracker = getRetryTracker();
        assertThat(retryTracker).containsEntry(100L, 1);
    }

    @Test
    void retryFailedTasks_WhenMultipleRetriesFailForSameWorkflow_ShouldRestartWorkflow() {
        // Arrange
        TaskExecution taskExecution1 = createTaskForRetry(1L, 100L, 1);
        TaskExecution taskExecution2 = createTaskForRetry(2L, 100L, 2);
        TaskExecution taskExecution3 = createTaskForRetry(3L, 100L, 3);

        // First call returns tasks to retry
        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(taskExecution1));

        // Make the execution fail
        doThrow(new RuntimeException("Retry failed"))
                .when(taskExecutionService).executeTask(1L);

        // Act - call three times to trigger the workflow restart threshold
        retryScheduler.retryFailedTasks();
        retryScheduler.retryFailedTasks();
        retryScheduler.retryFailedTasks();

        // Assert
        verify(workflowEngine).executeWorkflow(100L);

        // Verify retry tracker is cleared after restart
        Map<Long, Integer> retryTracker = getRetryTracker();
        assertThat(retryTracker).doesNotContainKey(100L);
    }

    @Test
    void retryFailedTasks_WhenWorkflowRestartFails_ShouldLogError() {
        // Arrange
        TaskExecution taskExecution = createTaskForRetry(1L, 100L, 1);

        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(taskExecution));
        doThrow(new RuntimeException("Task retry failed"))
                .when(taskExecutionService).executeTask(1L);
        doThrow(new RuntimeException("Workflow restart failed"))
                .when(workflowEngine).executeWorkflow(100L);

        // Act - call three times to trigger workflow restart
        retryScheduler.retryFailedTasks();
        retryScheduler.retryFailedTasks();
        retryScheduler.retryFailedTasks();

        // Assert
        verify(workflowEngine).executeWorkflow(100L);
        // Test passes if no exception is thrown (error is logged but not propagated)
    }

    @Test
    void retryFailedTasks_WithNoTasksToRetry_ShouldNotPerformAnyActions() {
        // Arrange
        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList());

        // Act
        retryScheduler.retryFailedTasks();

        // Assert
        verify(taskExecutionService).getTasksToRetry(any(LocalDateTime.class));
        verify(taskExecutionService, never()).saveTaskExecution(any());
        verify(taskExecutionService, never()).executeTask(any());
        verify(workflowEngine, never()).executeWorkflow(any());
    }

    @Test
    void retryFailedTasks_WithMixedSuccessAndFailure_ShouldHandleCorrectly() {
        // Arrange
        TaskExecution successTask = createTaskForRetry(1L, 100L, 1);
        TaskExecution failTask = createTaskForRetry(2L, 200L, 1);
        TaskExecution anotherSuccessTask = createTaskForRetry(3L, 300L, 1);

        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(successTask, failTask, anotherSuccessTask));

        // Mock successful execution for tasks 1 and 3 - return completed CompletableFuture
        when(taskExecutionService.executeTask(1L))
                .thenReturn(CompletableFuture.completedFuture(successTask));
        when(taskExecutionService.executeTask(3L))
                .thenReturn(CompletableFuture.completedFuture(anotherSuccessTask));

        // Mock failure for task 2
        doThrow(new RuntimeException("Task retry failed"))
                .when(taskExecutionService).executeTask(2L);

        // Act
        retryScheduler.retryFailedTasks();

        // Assert
        verify(taskExecutionService).executeTask(1L);
        verify(taskExecutionService).executeTask(2L);
        verify(taskExecutionService).executeTask(3L);

        // Verify only the failed workflow is tracked
        Map<Long, Integer> retryTracker = getRetryTracker();
        assertThat(retryTracker).containsEntry(200L, 1);
        assertThat(retryTracker).doesNotContainKey(100L);
        assertThat(retryTracker).doesNotContainKey(300L);
    }

    @Test
    void cleanupRetryTracker_ShouldClearAllEntries() {
        // Arrange - Add some entries to the retry tracker
        TaskExecution taskExecution = createTaskForRetry(1L, 100L, 1);
        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(taskExecution));
        doThrow(new RuntimeException("Retry failed"))
                .when(taskExecutionService).executeTask(1L);

        // Add entries to the tracker
        retryScheduler.retryFailedTasks();

        // Verify tracker has entries
        Map<Long, Integer> retryTracker = getRetryTracker();
        assertThat(retryTracker).isNotEmpty();

        // Act
        retryScheduler.cleanupRetryTracker();

        // Assert
        assertThat(retryTracker).isEmpty();
    }

    @Test
    void retryFailedTasks_WithDifferentRetryCountsForSameWorkflow_ShouldAccumulateRetries() {
        // Arrange
        TaskExecution taskExecution1 = createTaskForRetry(1L, 100L, 1);

        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(taskExecution1));
        doThrow(new RuntimeException("Retry failed"))
                .when(taskExecutionService).executeTask(1L);

        // Act - call multiple times
        retryScheduler.retryFailedTasks();
        retryScheduler.retryFailedTasks();

        // Assert
        Map<Long, Integer> retryTracker = getRetryTracker();
        assertThat(retryTracker).containsEntry(100L, 2);
    }

    @Test
    void retryFailedTasks_ShouldResetTaskExecutionFields() {
        // Arrange
        TaskExecution taskExecution = createTaskForRetry(1L, 100L, 1);
        taskExecution.setStartedAt(LocalDateTime.now().minusMinutes(5));
        taskExecution.setCompletedAt(LocalDateTime.now().minusMinutes(4));

        when(taskExecutionService.getTasksToRetry(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(taskExecution));

        // Act
        retryScheduler.retryFailedTasks();

        // Assert
        verify(taskExecutionService).saveTaskExecution(argThat(task -> {
            assertThat(task.getStartedAt()).isNull();
            assertThat(task.getCompletedAt()).isNull();
            assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
            return true;
        }));
    }

    private TaskExecution createTaskForRetry(Long taskId, Long workflowId, int retryCount) {
        TaskExecution taskExecution = new TaskExecution();
        taskExecution.setId(taskId);
        taskExecution.setWorkflowExecutionId(workflowId);
        taskExecution.setStatus(TaskStatus.AWAITING_RETRY);
        taskExecution.setRetryCount(retryCount);
        taskExecution.setNextRetryAt(LocalDateTime.now().minusMinutes(1));

        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setId(taskId);
        taskDefinition.setName("test-task-" + taskId);
        taskDefinition.setType("rest-api");
        taskExecution.setTaskDefinition(taskDefinition);

        return taskExecution;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Integer> getRetryTracker() {
        return (Map<Long, Integer>) ReflectionTestUtils.getField(retryScheduler, "workflowRetryTracker");
    }
}
