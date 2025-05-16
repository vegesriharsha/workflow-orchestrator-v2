package com.example.workfloworchestrator.engine.scheduler;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.EventPublisherService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowSchedulerTest {

    @Mock
    private WorkflowExecutionService workflowExecutionService;

    @Mock
    private EventPublisherService eventPublisherService;

    @Mock
    private WorkflowEngine workflowEngine;

    private WorkflowScheduler workflowScheduler;

    @BeforeEach
    void setUp() {
        workflowScheduler = new WorkflowScheduler(
                workflowExecutionService,
                eventPublisherService,
                workflowEngine
        );

        // Set configuration properties using reflection
        ReflectionTestUtils.setField(workflowScheduler, "stuckWorkflowTimeoutMinutes", 30);
        ReflectionTestUtils.setField(workflowScheduler, "stuckWorkflowAutoRetry", true);
        ReflectionTestUtils.setField(workflowScheduler, "completedWorkflowRetentionDays", 30);
    }

    @Test
    void checkStuckWorkflows_ShouldDetectAndRetryStuckWorkflows() {
        // Arrange
        WorkflowExecution stuckWorkflow1 = createStuckWorkflow(1L, LocalDateTime.now().minusHours(1));
        WorkflowExecution stuckWorkflow2 = createStuckWorkflow(2L, LocalDateTime.now().minusHours(2));
        List<WorkflowExecution> stuckWorkflows = Arrays.asList(stuckWorkflow1, stuckWorkflow2);

        when(workflowExecutionService.getStuckWorkflowExecutions(any(LocalDateTime.class)))
                .thenReturn(stuckWorkflows);

        // Act
        workflowScheduler.checkStuckWorkflows();

        // Assert
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(workflowExecutionService).getStuckWorkflowExecutions(thresholdCaptor.capture());

        LocalDateTime capturedThreshold = thresholdCaptor.getValue();
        assertThat(capturedThreshold).isBefore(LocalDateTime.now().minusMinutes(29));
        assertThat(capturedThreshold).isAfter(LocalDateTime.now().minusMinutes(31));

        // Verify each stuck workflow is retried
        verify(workflowEngine).executeWorkflow(1L);
        verify(workflowEngine).executeWorkflow(2L);

        // Verify no status updates (since auto-retry succeeded)
        verify(workflowExecutionService, never()).updateWorkflowExecutionStatus(anyLong(), eq(WorkflowStatus.FAILED));
        verify(eventPublisherService, never()).publishWorkflowFailedEvent(any());
    }

    @Test
    void checkStuckWorkflows_WhenAutoRetryDisabled_ShouldMarkAsFailed() {
        // Arrange
        ReflectionTestUtils.setField(workflowScheduler, "stuckWorkflowAutoRetry", false);

        WorkflowExecution stuckWorkflow = createStuckWorkflow(1L, LocalDateTime.now().minusHours(1));
        List<WorkflowExecution> stuckWorkflows = Arrays.asList(stuckWorkflow);

        when(workflowExecutionService.getStuckWorkflowExecutions(any(LocalDateTime.class)))
                .thenReturn(stuckWorkflows);

        // Act
        workflowScheduler.checkStuckWorkflows();

        // Assert
        verify(workflowEngine, never()).executeWorkflow(anyLong());
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.FAILED);
        verify(eventPublisherService).publishWorkflowFailedEvent(stuckWorkflow);

        assertThat(stuckWorkflow.getErrorMessage()).isEqualTo("Workflow execution timed out");
    }

    @Test
    void checkStuckWorkflows_When_RestartFails_ShouldMarkAsFailed() {
        // Arrange
        WorkflowExecution stuckWorkflow = createStuckWorkflow(1L, LocalDateTime.now().minusHours(1));
        List<WorkflowExecution> stuckWorkflows = Arrays.asList(stuckWorkflow);

        when(workflowExecutionService.getStuckWorkflowExecutions(any(LocalDateTime.class)))
                .thenReturn(stuckWorkflows);
        doThrow(new RuntimeException("Failed to restart"))
                .when(workflowEngine).executeWorkflow(1L);

        // Act
        workflowScheduler.checkStuckWorkflows();

        // Assert
        verify(workflowEngine).executeWorkflow(1L);
        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.FAILED);
        verify(eventPublisherService).publishWorkflowFailedEvent(stuckWorkflow);

        assertThat(stuckWorkflow.getErrorMessage())
                .startsWith("Workflow execution timed out and auto-retry failed:");
    }

    @Test
    void checkStuckWorkflows_WithNoStuckWorkflows_ShouldNotPerformAnyActions() {
        // Arrange
        when(workflowExecutionService.getStuckWorkflowExecutions(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList());

        // Act
        workflowScheduler.checkStuckWorkflows();

        // Assert
        verify(workflowEngine, never()).executeWorkflow(anyLong());
        verify(workflowExecutionService, never()).updateWorkflowExecutionStatus(anyLong(), any());
        verify(eventPublisherService, never()).publishWorkflowFailedEvent(any());
    }

    @Test
    void cleanupOldWorkflowExecutions_ShouldDeleteOldWorkflows() {
        // Arrange
        WorkflowExecution oldWorkflow1 = createCompletedWorkflow(1L, LocalDateTime.now().minusDays(35));
        WorkflowExecution oldWorkflow2 = createCompletedWorkflow(2L, LocalDateTime.now().minusDays(40));
        List<WorkflowExecution> oldWorkflows = Arrays.asList(oldWorkflow1, oldWorkflow2);

        when(workflowExecutionService.findCompletedWorkflowsOlderThan(any(LocalDateTime.class)))
                .thenReturn(oldWorkflows);

        // Act
        workflowScheduler.cleanupOldWorkflowExecutions();

        // Assert
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(workflowExecutionService).findCompletedWorkflowsOlderThan(thresholdCaptor.capture());

        LocalDateTime capturedThreshold = thresholdCaptor.getValue();
        assertThat(capturedThreshold).isBefore(LocalDateTime.now().minusDays(29));
        assertThat(capturedThreshold).isAfter(LocalDateTime.now().minusDays(31));

        // Verify each old workflow is deleted
        verify(workflowExecutionService).deleteWorkflowExecution(1L);
        verify(workflowExecutionService).deleteWorkflowExecution(2L);
    }

    @Test
    void cleanupOldWorkflowExecutions_WhenDeletionFails_ShouldContinueWithOthers() {
        // Arrange
        WorkflowExecution oldWorkflow1 = createCompletedWorkflow(1L, LocalDateTime.now().minusDays(35));
        WorkflowExecution oldWorkflow2 = createCompletedWorkflow(2L, LocalDateTime.now().minusDays(40));
        List<WorkflowExecution> oldWorkflows = Arrays.asList(oldWorkflow1, oldWorkflow2);

        when(workflowExecutionService.findCompletedWorkflowsOlderThan(any(LocalDateTime.class)))
                .thenReturn(oldWorkflows);
        doThrow(new RuntimeException("Failed to delete"))
                .when(workflowExecutionService).deleteWorkflowExecution(1L);

        // Act
        workflowScheduler.cleanupOldWorkflowExecutions();

        // Assert
        verify(workflowExecutionService).deleteWorkflowExecution(1L);
        verify(workflowExecutionService).deleteWorkflowExecution(2L);
    }

    @Test
    void cleanupOldWorkflowExecutions_WithNoOldWorkflows_ShouldNotPerformAnyDeletions() {
        // Arrange
        when(workflowExecutionService.findCompletedWorkflowsOlderThan(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList());

        // Act
        workflowScheduler.cleanupOldWorkflowExecutions();

        // Assert
        verify(workflowExecutionService, never()).deleteWorkflowExecution(anyLong());
    }

    @Test
    void checkPausedWorkflows_ShouldDetectLongPausedWorkflows() {
        // Arrange
        WorkflowExecution pausedWorkflow1 = createPausedWorkflow(1L, LocalDateTime.now().minusHours(25));
        WorkflowExecution pausedWorkflow2 = createPausedWorkflow(2L, LocalDateTime.now().minusHours(30));
        List<WorkflowExecution> pausedWorkflows = Arrays.asList(pausedWorkflow1, pausedWorkflow2);

        when(workflowExecutionService.findPausedWorkflowsOlderThan(any(LocalDateTime.class)))
                .thenReturn(pausedWorkflows);

        // Act
        workflowScheduler.checkPausedWorkflows();

        // Assert
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(workflowExecutionService).findPausedWorkflowsOlderThan(thresholdCaptor.capture());

        LocalDateTime capturedThreshold = thresholdCaptor.getValue();
        assertThat(capturedThreshold).isBefore(LocalDateTime.now().minusHours(23));
        assertThat(capturedThreshold).isAfter(LocalDateTime.now().minusHours(25));

        // This method only logs - no actions taken
        verify(workflowEngine, never()).executeWorkflow(anyLong());
        verify(workflowExecutionService, never()).updateWorkflowExecutionStatus(anyLong(), any());
    }

    @Test
    void checkPausedWorkflows_WithNoPausedWorkflows_ShouldNotLogWarnings() {
        // Arrange
        when(workflowExecutionService.findPausedWorkflowsOlderThan(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList());

        // Act
        workflowScheduler.checkPausedWorkflows();

        // Assert - no exceptions thrown means the method handled the empty list correctly
        verify(workflowExecutionService).findPausedWorkflowsOlderThan(any(LocalDateTime.class));
    }

    @Test
    void checkStuckWorkflows_WithCustomTimeoutConfiguration_ShouldUseConfiguredValue() {
        // Arrange
        ReflectionTestUtils.setField(workflowScheduler, "stuckWorkflowTimeoutMinutes", 60);

        WorkflowExecution stuckWorkflow = createStuckWorkflow(1L, LocalDateTime.now().minusMinutes(90));
        List<WorkflowExecution> stuckWorkflows = Arrays.asList(stuckWorkflow);

        when(workflowExecutionService.getStuckWorkflowExecutions(any(LocalDateTime.class)))
                .thenReturn(stuckWorkflows);

        // Act
        workflowScheduler.checkStuckWorkflows();

        // Assert
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(workflowExecutionService).getStuckWorkflowExecutions(thresholdCaptor.capture());

        LocalDateTime capturedThreshold = thresholdCaptor.getValue();
        assertThat(capturedThreshold).isBefore(LocalDateTime.now().minusMinutes(59));
        assertThat(capturedThreshold).isAfter(LocalDateTime.now().minusMinutes(61));
    }

    @Test
    void cleanupOldWorkflowExecutions_WithCustomRetentionPeriod_ShouldUseConfiguredValue() {
        // Arrange
        ReflectionTestUtils.setField(workflowScheduler, "completedWorkflowRetentionDays", 7);

        WorkflowExecution oldWorkflow = createCompletedWorkflow(1L, LocalDateTime.now().minusDays(10));
        List<WorkflowExecution> oldWorkflows = Arrays.asList(oldWorkflow);

        when(workflowExecutionService.findCompletedWorkflowsOlderThan(any(LocalDateTime.class)))
                .thenReturn(oldWorkflows);

        // Act
        workflowScheduler.cleanupOldWorkflowExecutions();

        // Assert
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(workflowExecutionService).findCompletedWorkflowsOlderThan(thresholdCaptor.capture());

        LocalDateTime capturedThreshold = thresholdCaptor.getValue();
        assertThat(capturedThreshold).isBefore(LocalDateTime.now().minusDays(6));
        assertThat(capturedThreshold).isAfter(LocalDateTime.now().minusDays(8));
    }

    private WorkflowExecution createStuckWorkflow(Long id, LocalDateTime startedAt) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(id);
        execution.setStatus(WorkflowStatus.RUNNING);
        execution.setStartedAt(startedAt);
        execution.setCorrelationId("stuck-" + id);
        return execution;
    }

    private WorkflowExecution createCompletedWorkflow(Long id, LocalDateTime completedAt) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(id);
        execution.setStatus(WorkflowStatus.COMPLETED);
        execution.setCompletedAt(completedAt);
        execution.setCorrelationId("completed-" + id);
        return execution;
    }

    private WorkflowExecution createPausedWorkflow(Long id, LocalDateTime startedAt) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(id);
        execution.setStatus(WorkflowStatus.PAUSED);
        execution.setStartedAt(startedAt);
        execution.setCorrelationId("paused-" + id);
        return execution;
    }
}
