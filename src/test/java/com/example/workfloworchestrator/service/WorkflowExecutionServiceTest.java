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
import org.mockito.junit.jupiter.MockitoExtension;

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

        WorkflowExecution expectedExecution = new WorkflowExecution();
        expectedExecution.setId(1L);
        expectedExecution.setWorkflowDefinition(mockDefinition);
        expectedExecution.setStatus(WorkflowStatus.CREATED);

        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenReturn(expectedExecution);

        // Act
        WorkflowExecution result = workflowExecutionService.startWorkflow(workflowName, version, variables);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getWorkflowDefinition()).isEqualTo(mockDefinition);
        verify(workflowEngine).executeWorkflow(1L);
    }

    @Test
    void startWorkflow_WithNullVersion_ShouldUseLatestVersion() {
        // Arrange
        String workflowName = "test-workflow";
        WorkflowDefinition mockDefinition = new WorkflowDefinition();
        mockDefinition.setId(1L);

        when(workflowService.getLatestWorkflowDefinition(workflowName))
                .thenReturn(Optional.of(mockDefinition));

        when(workflowExecutionRepository.save(any(WorkflowExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        WorkflowExecution result = workflowExecutionService.startWorkflow(workflowName, null, null);

        // Assert
        assertThat(result).isNotNull();
        verify(workflowService).getLatestWorkflowDefinition(workflowName);
    }

}
