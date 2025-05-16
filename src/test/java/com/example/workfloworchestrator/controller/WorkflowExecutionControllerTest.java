package com.example.workfloworchestrator.controller;

import com.example.workfloworchestrator.exception.GlobalExceptionHandler;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({WorkflowExecutionController.class, GlobalExceptionHandler.class})
class WorkflowExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowExecutionService workflowExecutionService;

    @Autowired
    private ObjectMapper objectMapper;

    private WorkflowExecution mockWorkflowExecution;
    private WorkflowDefinition mockWorkflowDefinition;

    @BeforeEach
    void setUp() {
        // Create mock workflow definition
        mockWorkflowDefinition = new WorkflowDefinition();
        mockWorkflowDefinition.setId(1L);
        mockWorkflowDefinition.setName("test-workflow");
        mockWorkflowDefinition.setDescription("Test workflow");
        mockWorkflowDefinition.setVersion("1.0.0");
        mockWorkflowDefinition.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        // Create mock workflow execution
        mockWorkflowExecution = new WorkflowExecution();
        mockWorkflowExecution.setId(1L);
        mockWorkflowExecution.setWorkflowDefinition(mockWorkflowDefinition);
        mockWorkflowExecution.setCorrelationId("test-correlation-id");
        mockWorkflowExecution.setStatus(WorkflowStatus.CREATED);
        mockWorkflowExecution.setStartedAt(LocalDateTime.now());
        mockWorkflowExecution.setCurrentTaskIndex(0);
        mockWorkflowExecution.setRetryCount(0);
        mockWorkflowExecution.setVariables(Map.of("key1", "value1", "key2", "value2"));
    }

    @Test
    void startWorkflow_ShouldCreateAndStartWorkflow() throws Exception {
        // Arrange
        String workflowName = "test-workflow";
        String version = "1.0.0";
        Map<String, String> variables = Map.of("param1", "value1", "param2", "value2");

        when(workflowExecutionService.startWorkflow(eq(workflowName), eq(version), any()))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(post("/api/executions/start")
                        .param("workflowName", workflowName)
                        .param("version", version)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(variables)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.correlationId", is("test-correlation-id")))
                .andExpect(jsonPath("$.status", is("CREATED")))
                .andExpect(jsonPath("$.workflowDefinition.name", is("test-workflow")))
                .andExpect(jsonPath("$.workflowDefinition.version", is("1.0.0")));

        verify(workflowExecutionService).startWorkflow(eq(workflowName), eq(version), eq(variables));
    }

    @Test
    void startWorkflow_WithoutVersion_ShouldUseLatestVersion() throws Exception {
        // Arrange
        String workflowName = "test-workflow";
        Map<String, String> variables = Map.of("param1", "value1");

        when(workflowExecutionService.startWorkflow(eq(workflowName), isNull(), any()))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(post("/api/executions/start")
                        .param("workflowName", workflowName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(variables)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)));

        verify(workflowExecutionService).startWorkflow(eq(workflowName), isNull(), eq(variables));
    }

    @Test
    void startWorkflow_WithoutVariables_ShouldStartWithEmptyVariables() throws Exception {
        // Arrange
        String workflowName = "test-workflow";

        when(workflowExecutionService.startWorkflow(eq(workflowName), isNull(), any()))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(post("/api/executions/start")
                        .param("workflowName", workflowName))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)));

        verify(workflowExecutionService).startWorkflow(eq(workflowName), isNull(), eq(Map.of()));
    }

    @Test
    void getWorkflowExecution_ShouldReturnExecution() throws Exception {
        // Arrange
        Long executionId = 1L;
        when(workflowExecutionService.getWorkflowExecution(executionId))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(get("/api/executions/{id}", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.correlationId", is("test-correlation-id")))
                .andExpect(jsonPath("$.status", is("CREATED")));

        verify(workflowExecutionService).getWorkflowExecution(executionId);
    }

    @Test
    void getWorkflowExecutionByCorrelationId_ShouldReturnExecution() throws Exception {
        // Arrange
        String correlationId = "test-correlation-id";
        when(workflowExecutionService.getWorkflowExecutionByCorrelationId(correlationId))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(get("/api/executions/correlation/{correlationId}", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.correlationId", is("test-correlation-id")));

        verify(workflowExecutionService).getWorkflowExecutionByCorrelationId(correlationId);
    }

    @Test
    void getWorkflowExecutionsByStatus_ShouldReturnFilteredExecutions() throws Exception {
        // Arrange
        WorkflowStatus status = WorkflowStatus.RUNNING;
        List<WorkflowExecution> executions = List.of(mockWorkflowExecution);

        when(workflowExecutionService.getWorkflowExecutionsByStatus(status))
                .thenReturn(executions);

        // Act & Assert
        mockMvc.perform(get("/api/executions")
                        .param("status", status.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)));

        verify(workflowExecutionService).getWorkflowExecutionsByStatus(status);
    }

    @Test
    void getWorkflowExecutionsByStatus_WithoutStatus_ShouldReturnAllExecutions() throws Exception {
        // Arrange
        List<WorkflowExecution> executions = List.of(mockWorkflowExecution);

        when(workflowExecutionService.getAllWorkflowExecutions())
                .thenReturn(executions);

        // Act & Assert
        mockMvc.perform(get("/api/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)));

        verify(workflowExecutionService).getAllWorkflowExecutions();
        verify(workflowExecutionService, never()).getWorkflowExecutionsByStatus(any());
    }

    @Test
    void pauseWorkflowExecution_ShouldPauseExecution() throws Exception {
        // Arrange
        Long executionId = 1L;

        mockWorkflowExecution.setStatus(WorkflowStatus.PAUSED);
        when(workflowExecutionService.pauseWorkflowExecution(executionId))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(post("/api/executions/{id}/pause", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.status", is("PAUSED")));

        verify(workflowExecutionService).pauseWorkflowExecution(executionId);
    }

    @Test
    void resumeWorkflowExecution_ShouldResumeExecution() throws Exception {
        // Arrange
        Long executionId = 1L;

        mockWorkflowExecution.setStatus(WorkflowStatus.RUNNING);
        when(workflowExecutionService.resumeWorkflowExecution(executionId))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(post("/api/executions/{id}/resume", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.status", is("RUNNING")));

        verify(workflowExecutionService).resumeWorkflowExecution(executionId);
    }

    @Test
    void cancelWorkflowExecution_ShouldCancelExecution() throws Exception {
        // Arrange
        Long executionId = 1L;

        mockWorkflowExecution.setStatus(WorkflowStatus.CANCELLED);
        mockWorkflowExecution.setCompletedAt(LocalDateTime.now());
        when(workflowExecutionService.cancelWorkflowExecution(executionId))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(post("/api/executions/{id}/cancel", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        verify(workflowExecutionService).cancelWorkflowExecution(executionId);
    }

    @Test
    void retryWorkflowExecution_ShouldRetryExecution() throws Exception {
        // Arrange
        Long executionId = 1L;

        mockWorkflowExecution.setStatus(WorkflowStatus.RUNNING);
        mockWorkflowExecution.setRetryCount(1);
        when(workflowExecutionService.retryWorkflowExecution(executionId))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(post("/api/executions/{id}/retry", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.status", is("RUNNING")))
                .andExpect(jsonPath("$.retryCount", is(1)));

        verify(workflowExecutionService).retryWorkflowExecution(executionId);
    }

    @Test
    void retryWorkflowExecutionSubset_ShouldRetrySpecificTasks() throws Exception {
        // Arrange
        Long executionId = 1L;
        List<Long> taskIds = List.of(2L, 3L, 4L);

        mockWorkflowExecution.setStatus(WorkflowStatus.RUNNING);
        when(workflowExecutionService.retryWorkflowExecutionSubset(executionId, taskIds))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(post("/api/executions/{id}/retry-subset", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.status", is("RUNNING")));

        verify(workflowExecutionService).retryWorkflowExecutionSubset(executionId, taskIds);
    }

    @Test
    void retryWorkflowExecutionSubset_WithEmptyTaskIds_ShouldStillWork() throws Exception {
        // Arrange
        Long executionId = 1L;
        List<Long> taskIds = List.of();

        when(workflowExecutionService.retryWorkflowExecutionSubset(executionId, taskIds))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(post("/api/executions/{id}/retry-subset", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)));

        verify(workflowExecutionService).retryWorkflowExecutionSubset(executionId, taskIds);
    }

    @Test
    void startWorkflow_WithMissingWorkflowName_ShouldReturn400() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/executions/start"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Missing Required Parameter")))
                .andExpect(jsonPath("$.message", containsString("workflowName")));

        verify(workflowExecutionService, never()).startWorkflow(any(), any(), any());
    }

    @Test
    void getWorkflowExecutionsByStatus_WithMultipleStatuses_ShouldReturnCorrectly() throws Exception {
        // Arrange
        WorkflowExecution execution1 = new WorkflowExecution();
        execution1.setId(1L);
        execution1.setStatus(WorkflowStatus.RUNNING);
        execution1.setCorrelationId("correlation-1");

        WorkflowExecution execution2 = new WorkflowExecution();
        execution2.setId(2L);
        execution2.setStatus(WorkflowStatus.RUNNING);
        execution2.setCorrelationId("correlation-2");

        List<WorkflowExecution> runningExecutions = List.of(execution1, execution2);

        when(workflowExecutionService.getWorkflowExecutionsByStatus(WorkflowStatus.RUNNING))
                .thenReturn(runningExecutions);

        // Act & Assert
        mockMvc.perform(get("/api/executions")
                        .param("status", "RUNNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].status", is("RUNNING")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].status", is("RUNNING")));

        verify(workflowExecutionService).getWorkflowExecutionsByStatus(WorkflowStatus.RUNNING);
    }

    @Test
    void getWorkflowExecution_WithComplexExecution_ShouldReturnFullDetails() throws Exception {
        // Arrange
        Long executionId = 1L;

        // Add task executions
        TaskExecution taskExecution1 = new TaskExecution();
        taskExecution1.setId(10L);
        taskExecution1.setStatus(TaskStatus.COMPLETED);

        TaskExecution taskExecution2 = new TaskExecution();
        taskExecution2.setId(11L);
        taskExecution2.setStatus(TaskStatus.RUNNING);

        mockWorkflowExecution.getTaskExecutions().add(taskExecution1);
        mockWorkflowExecution.getTaskExecutions().add(taskExecution2);

        // Add review points
        UserReviewPoint reviewPoint = new UserReviewPoint();
        reviewPoint.setId(20L);
        reviewPoint.setTaskExecutionId(11L);
        reviewPoint.setCreatedAt(LocalDateTime.now());

        mockWorkflowExecution.getReviewPoints().add(reviewPoint);

        when(workflowExecutionService.getWorkflowExecution(executionId))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(get("/api/executions/{id}", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.status", is("CREATED")))
                .andExpect(jsonPath("$.taskExecutions", hasSize(2)))
                .andExpect(jsonPath("$.taskExecutions[0].id", is(10)))
                .andExpect(jsonPath("$.taskExecutions[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$.taskExecutions[1].id", is(11)))
                .andExpect(jsonPath("$.taskExecutions[1].status", is("RUNNING")))
                .andExpect(jsonPath("$.reviewPoints", hasSize(1)))
                .andExpect(jsonPath("$.reviewPoints[0].id", is(20)))
                .andExpect(jsonPath("$.variables.key1", is("value1")))
                .andExpect(jsonPath("$.variables.key2", is("value2")));

        verify(workflowExecutionService).getWorkflowExecution(executionId);
    }

    @Test
    void startWorkflow_WithComplexVariables_ShouldHandleCorrectly() throws Exception {
        // Arrange
        String workflowName = "complex-workflow";
        Map<String, String> complexVariables = Map.of(
                "stringParam", "value",
                "numericParam", "123",
                "booleanParam", "true",
                "jsonParam", "{\"nested\": \"value\"}"
        );

        when(workflowExecutionService.startWorkflow(eq(workflowName), isNull(), any()))
                .thenReturn(mockWorkflowExecution);

        // Act & Assert
        mockMvc.perform(post("/api/executions/start")
                        .param("workflowName", workflowName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complexVariables)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)));

        verify(workflowExecutionService).startWorkflow(eq(workflowName), isNull(), eq(complexVariables));
    }

    @Test
    void getAllExecutions_WithDifferentStatuses_ShouldReturnAll() throws Exception {
        // Arrange
        WorkflowExecution execution1 = new WorkflowExecution();
        execution1.setId(1L);
        execution1.setStatus(WorkflowStatus.COMPLETED);
        execution1.setCorrelationId("correlation-1");

        WorkflowExecution execution2 = new WorkflowExecution();
        execution2.setId(2L);
        execution2.setStatus(WorkflowStatus.FAILED);
        execution2.setCorrelationId("correlation-2");

        WorkflowExecution execution3 = new WorkflowExecution();
        execution3.setId(3L);
        execution3.setStatus(WorkflowStatus.RUNNING);
        execution3.setCorrelationId("correlation-3");

        List<WorkflowExecution> allExecutions = List.of(execution1, execution2, execution3);

        when(workflowExecutionService.getAllWorkflowExecutions())
                .thenReturn(allExecutions);

        // Act & Assert
        mockMvc.perform(get("/api/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$[1].status", is("FAILED")))
                .andExpect(jsonPath("$[2].status", is("RUNNING")));

        verify(workflowExecutionService).getAllWorkflowExecutions();
    }

    @Test
    void operationsOnNonExistentExecution_ShouldHandleGracefully() throws Exception {
        // This test would typically result in exceptions from the service layer
        // The controller would let these propagate and be handled by the GlobalExceptionHandler

        Long nonExistentId = 999L;

        when(workflowExecutionService.getWorkflowExecution(nonExistentId))
                .thenThrow(new RuntimeException("Execution not found"));

        // The controller doesn't handle exceptions itself, so this would result in a 500
        // In a real application, GlobalExceptionHandler would catch this and return appropriate HTTP status
        mockMvc.perform(get("/api/executions/{id}", nonExistentId))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void retryWorkflowExecutionSubset_WithNullBody_ShouldReturn400() throws Exception {
        // Arrange
        Long executionId = 1L;

        // Act & Assert
        mockMvc.perform(post("/api/executions/{id}/retry-subset", executionId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Invalid Request Body")))
                .andExpect(jsonPath("$.message", containsString("Required request body is missing")));

        verify(workflowExecutionService, never()).retryWorkflowExecutionSubset(any(), any());
    }

    private WorkflowExecution createWorkflowExecution(Long id, WorkflowStatus status, String correlationId) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(id);
        execution.setStatus(status);
        execution.setCorrelationId(correlationId);
        execution.setStartedAt(LocalDateTime.now());
        execution.setRetryCount(0);
        return execution;
    }
}
