package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import com.example.workfloworchestrator.repository.WorkflowExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutionServiceTest {

    @Mock
    private WorkflowService workflowService;

    @Mock
    private WorkflowExecutionRepository workflowExecutionRepository;

    @Mock
    private WorkflowEngine workflowEngine;

    @Mock
    private EventPublisherService eventPublisherService;

    private WorkflowExecutionService workflowExecutionService;

    @BeforeEach
    void setUp() {
        workflowExecutionService = new WorkflowExecutionService(
                workflowService,
                workflowExecutionRepository,
                eventPublisherService
        );
        workflowExecutionService.setWorkflowEngine(workflowEngine);
    }

    @Test
    void startWorkflow_ShouldCreateAndExecuteWorkflow() {
        // Arrange
        String workflowName = "test-workflow";
        String version = "1.0.0";
        Map<String, String> variables = Map.of("key", "value");

        WorkflowDefinition mockDefinition = new WorkflowDefinition();
        mockDefinition.setId(1L);
        mockDefinition.setName(workflowName);
        mockDefinition.setVersion(version);

        when(workflowService.getWorkflowDefinition(workflowName, version))
                .thenReturn(Optional.of(mockDefinition));

        WorkflowExecution savedExecution = new WorkflowExecution();
        savedExecution.setId(1L);
        savedExecution.setWorkflowDefinition(mockDefinition);
        savedExecution.setStatus(WorkflowStatus.CREATED);
        savedExecution.setVariables(variables);

        when(workflowExecutionRepository.saveAndFlush(any(WorkflowExecution.class)))
                .thenReturn(savedExecution);

        // Mock TransactionSynchronizationManager to simulate active transaction
        try (MockedStatic<TransactionSynchronizationManager> tsm = Mockito.mockStatic(TransactionSynchronizationManager.class)) {
            tsm.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);

            // Act
            WorkflowExecution result = workflowExecutionService.startWorkflow(workflowName, version, variables);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getWorkflowDefinition()).isEqualTo(mockDefinition);
            assertThat(result.getStatus()).isEqualTo(WorkflowStatus.CREATED);
            assertThat(result.getVariables()).isEqualTo(variables);

            verify(workflowService).getWorkflowDefinition(workflowName, version);
            verify(workflowExecutionRepository).saveAndFlush(any(WorkflowExecution.class));

            // Verify that synchronization was attempted to be registered
            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()));
        }
    }

    @Test
    void startWorkflow_WithNullVersion_ShouldUseLatestVersion() {
        // Arrange
        String workflowName = "test-workflow";
        WorkflowDefinition mockDefinition = new WorkflowDefinition();
        mockDefinition.setId(1L);
        mockDefinition.setName(workflowName);
        mockDefinition.setVersion("1.0.0");

        when(workflowService.getLatestWorkflowDefinition(workflowName))
                .thenReturn(Optional.of(mockDefinition));

        WorkflowExecution savedExecution = new WorkflowExecution();
        savedExecution.setId(1L);
        savedExecution.setWorkflowDefinition(mockDefinition);
        savedExecution.setStatus(WorkflowStatus.CREATED);

        when(workflowExecutionRepository.saveAndFlush(any(WorkflowExecution.class)))
                .thenReturn(savedExecution);

        // Mock TransactionSynchronizationManager
        try (MockedStatic<TransactionSynchronizationManager> tsm = Mockito.mockStatic(TransactionSynchronizationManager.class)) {
            tsm.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);

            // Act
            WorkflowExecution result = workflowExecutionService.startWorkflow(workflowName, null, null);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getWorkflowDefinition()).isEqualTo(mockDefinition);
            verify(workflowService).getLatestWorkflowDefinition(workflowName);
        }
    }

    @Test
    void getWorkflowExecution_ShouldReturnExecution() {
        // Arrange
        Long executionId = 1L;
        WorkflowExecution mockExecution = new WorkflowExecution();
        mockExecution.setId(executionId);
        mockExecution.setStatus(WorkflowStatus.RUNNING);

        when(workflowExecutionRepository.findById(executionId))
                .thenReturn(Optional.of(mockExecution));

        // Act
        WorkflowExecution result = workflowExecutionService.getWorkflowExecution(executionId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(executionId);
        verify(workflowExecutionRepository).findById(executionId);
    }

    @Test
    void getWorkflowExecution_WhenNotFound_ShouldThrowException() {
        // Arrange
        Long executionId = 1L;
        when(workflowExecutionRepository.findById(executionId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> workflowExecutionService.getWorkflowExecution(executionId))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("Workflow execution not found with id: " + executionId);
    }

    @Test
    void pauseWorkflowExecution_ShouldPauseRunningWorkflow() {
        // Arrange
        Long executionId = 1L;
        WorkflowExecution mockExecution = new WorkflowExecution();
        mockExecution.setId(executionId);
        mockExecution.setStatus(WorkflowStatus.RUNNING);

        when(workflowExecutionRepository.findById(executionId))
                .thenReturn(Optional.of(mockExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(mockExecution);

        // Act
        WorkflowExecution result = workflowExecutionService.pauseWorkflowExecution(executionId);

        // Assert
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.PAUSED);
        verify(eventPublisherService).publishWorkflowPausedEvent(mockExecution);
    }

    @Test
    void resumeWorkflowExecution_ShouldResumeWorkflowAndExecute() {
        // Arrange
        Long executionId = 1L;
        WorkflowExecution mockExecution = new WorkflowExecution();
        mockExecution.setId(executionId);
        mockExecution.setStatus(WorkflowStatus.PAUSED);

        when(workflowExecutionRepository.findById(executionId))
                .thenReturn(Optional.of(mockExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(mockExecution);

        // Act
        WorkflowExecution result = workflowExecutionService.resumeWorkflowExecution(executionId);

        // Assert
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        verify(workflowEngine).executeWorkflow(executionId);
        verify(eventPublisherService).publishWorkflowResumedEvent(mockExecution);
    }

    @Test
    void updateWorkflowExecutionStatus_ShouldUpdateStatus() {
        // Arrange
        Long executionId = 1L;
        WorkflowStatus newStatus = WorkflowStatus.COMPLETED;

        WorkflowExecution mockExecution = new WorkflowExecution();
        mockExecution.setId(executionId);
        mockExecution.setStatus(WorkflowStatus.RUNNING);

        when(workflowExecutionRepository.findById(executionId))
                .thenReturn(Optional.of(mockExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        WorkflowExecution result = workflowExecutionService.updateWorkflowExecutionStatus(executionId, newStatus);

        // Assert
        assertThat(result.getStatus()).isEqualTo(newStatus);
        assertThat(result.getCompletedAt()).isNotNull(); // Should be set for terminal status
        verify(eventPublisherService).publishWorkflowStatusChangedEvent(mockExecution);
    }

    @Test
    void getWorkflowExecutionsByStatus_ShouldReturnFilteredExecutions() {
        // Arrange
        WorkflowStatus status = WorkflowStatus.RUNNING;
        List<WorkflowExecution> mockExecutions = Arrays.asList(
                createMockExecution(1L, status),
                createMockExecution(2L, status)
        );

        when(workflowExecutionRepository.findByStatus(status))
                .thenReturn(mockExecutions);

        // Act
        List<WorkflowExecution> result = workflowExecutionService.getWorkflowExecutionsByStatus(status);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> e.getStatus() == status);
        verify(workflowExecutionRepository).findByStatus(status);
    }

    @Test
    void startWorkflow_WhenDefinitionNotFound_ShouldThrowException() {
        // Arrange
        String workflowName = "non-existent-workflow";
        String version = "1.0.0";

        when(workflowService.getWorkflowDefinition(workflowName, version))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> workflowExecutionService.startWorkflow(workflowName, version, null))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("Workflow definition not found");
    }

    @Test
    void cancelWorkflowExecution_ShouldCancelWorkflow() {
        // Arrange
        Long executionId = 1L;
        WorkflowExecution mockExecution = new WorkflowExecution();
        mockExecution.setId(executionId);
        mockExecution.setStatus(WorkflowStatus.RUNNING);

        when(workflowExecutionRepository.findById(executionId))
                .thenReturn(Optional.of(mockExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        WorkflowExecution result = workflowExecutionService.cancelWorkflowExecution(executionId);

        // Assert
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
        assertThat(result.getCompletedAt()).isNotNull();
        verify(eventPublisherService).publishWorkflowCancelledEvent(mockExecution);
    }

    @Test
    void retryWorkflowExecution_ShouldRetryFailedWorkflow() {
        // Arrange
        Long executionId = 1L;
        WorkflowExecution mockExecution = new WorkflowExecution();
        mockExecution.setId(executionId);
        mockExecution.setStatus(WorkflowStatus.FAILED);
        mockExecution.setRetryCount(0);

        when(workflowExecutionRepository.findById(executionId))
                .thenReturn(Optional.of(mockExecution));
        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        WorkflowExecution result = workflowExecutionService.retryWorkflowExecution(executionId);

        // Assert
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(result.getRetryCount()).isEqualTo(1);
        verify(workflowEngine).executeWorkflow(executionId);
        verify(eventPublisherService).publishWorkflowRetryEvent(mockExecution);
    }

    // Alternative approach: Test without transaction synchronization
    @Test
    void startWorkflow_WithoutTransactionSync_ShouldCreateWorkflowOnly() {
        // Arrange
        String workflowName = "test-workflow";
        String version = "1.0.0";
        Map<String, String> variables = Map.of("key", "value");

        // Create a version without transaction synchronization for testing
        WorkflowExecutionService serviceWithoutTxSync = new WorkflowExecutionService(
                workflowService,
                workflowExecutionRepository,
                eventPublisherService
        ) {
            @Override
            public WorkflowExecution startWorkflow(String workflowName, String version, Map<String, String> variables) {
                // Call createWorkflowExecution directly to bypass transaction sync
                WorkflowDefinition definition = getWorkflowDefinition(workflowName, version);
                return createWorkflowExecution(definition, variables);
            }
        };
        serviceWithoutTxSync.setWorkflowEngine(workflowEngine);

        WorkflowDefinition mockDefinition = new WorkflowDefinition();
        mockDefinition.setId(1L);
        mockDefinition.setName(workflowName);
        mockDefinition.setVersion(version);

        when(workflowService.getWorkflowDefinition(workflowName, version))
                .thenReturn(Optional.of(mockDefinition));

        WorkflowExecution savedExecution = new WorkflowExecution();
        savedExecution.setId(1L);
        savedExecution.setWorkflowDefinition(mockDefinition);
        savedExecution.setStatus(WorkflowStatus.CREATED);

        when(workflowExecutionRepository.saveAndFlush(any(WorkflowExecution.class)))
                .thenReturn(savedExecution);

        // Act
        WorkflowExecution result = serviceWithoutTxSync.startWorkflow(workflowName, version, variables);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.CREATED);
    }

    private WorkflowExecution createMockExecution(Long id, WorkflowStatus status) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(id);
        execution.setStatus(status);
        execution.setCorrelationId(UUID.randomUUID().toString());
        return execution;
    }
}
