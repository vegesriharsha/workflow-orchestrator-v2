package com.example.workfloworchestrator.controller;

import com.example.workfloworchestrator.model.TaskDefinition;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkflowController.class)
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowService workflowService;

    @Autowired
    private ObjectMapper objectMapper;

    private WorkflowDefinition mockWorkflowDefinition;

    @BeforeEach
    void setUp() {
        mockWorkflowDefinition = new WorkflowDefinition();
        mockWorkflowDefinition.setId(1L);
        mockWorkflowDefinition.setName("test-workflow");
        mockWorkflowDefinition.setDescription("Test workflow description");
        mockWorkflowDefinition.setVersion("1.0.0");
        mockWorkflowDefinition.setCreatedAt(LocalDateTime.now());
        mockWorkflowDefinition.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        // Add a test task
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setId(1L);
        taskDefinition.setName("test-task");
        taskDefinition.setDescription("Test task description");
        taskDefinition.setType("rest-api");
        taskDefinition.setExecutionOrder(0);
        mockWorkflowDefinition.getTasks().add(taskDefinition);
    }

    @Test
    void getAllWorkflowDefinitions_ShouldReturnListOfWorkflows() throws Exception {
        // Arrange
        List<WorkflowDefinition> workflowDefinitions = List.of(mockWorkflowDefinition);
        when(workflowService.getAllWorkflowDefinitions()).thenReturn(workflowDefinitions);

        // Act & Assert
        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].name", is("test-workflow")))
                .andExpect(jsonPath("$[0].description", is("Test workflow description")))
                .andExpect(jsonPath("$[0].version", is("1.0.0")))
                .andExpect(jsonPath("$[0].strategyType", is("SEQUENTIAL")))
                .andExpect(jsonPath("$[0].tasks", hasSize(1)));

        verify(workflowService).getAllWorkflowDefinitions();
    }

    @Test
    void getWorkflowDefinition_ShouldReturnWorkflowById() throws Exception {
        // Arrange
        Long workflowId = 1L;
        when(workflowService.getWorkflowDefinition(workflowId)).thenReturn(Optional.of(mockWorkflowDefinition));

        // Act & Assert
        mockMvc.perform(get("/api/workflows/{id}", workflowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("test-workflow")))
                .andExpect(jsonPath("$.description", is("Test workflow description")));

        verify(workflowService).getWorkflowDefinition(workflowId);
    }

    @Test
    void getWorkflowDefinition_WhenNotFound_ShouldReturn404() throws Exception {
        // Arrange
        Long workflowId = 999L;
        when(workflowService.getWorkflowDefinition(workflowId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/workflows/{id}", workflowId))
                .andExpect(status().isNotFound());

        verify(workflowService).getWorkflowDefinition(workflowId);
    }

    @Test
    void getLatestWorkflowDefinition_ShouldReturnLatestVersion() throws Exception {
        // Arrange
        String workflowName = "test-workflow";
        when(workflowService.getLatestWorkflowDefinition(workflowName)).thenReturn(Optional.of(mockWorkflowDefinition));

        // Act & Assert
        mockMvc.perform(get("/api/workflows/name/{name}", workflowName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("test-workflow")))
                .andExpect(jsonPath("$.version", is("1.0.0")));

        verify(workflowService).getLatestWorkflowDefinition(workflowName);
    }

    @Test
    void getLatestWorkflowDefinition_WhenNotFound_ShouldReturn404() throws Exception {
        // Arrange 
        String workflowName = "non-existent-workflow";
        when(workflowService.getLatestWorkflowDefinition(workflowName)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/workflows/name/{name}", workflowName))
                .andExpect(status().isNotFound());

        verify(workflowService).getLatestWorkflowDefinition(workflowName);
    }

    @Test
    void getWorkflowDefinitionByNameAndVersion_ShouldReturnSpecificVersion() throws Exception {
        // Arrange
        String workflowName = "test-workflow";
        String version = "1.0.0";
        when(workflowService.getWorkflowDefinition(workflowName, version)).thenReturn(Optional.of(mockWorkflowDefinition));

        // Act & Assert
        mockMvc.perform(get("/api/workflows/name/{name}/version/{version}", workflowName, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("test-workflow")))
                .andExpect(jsonPath("$.version", is("1.0.0")));

        verify(workflowService).getWorkflowDefinition(workflowName, version);
    }

    @Test
    void getWorkflowDefinitionByNameAndVersion_WhenNotFound_ShouldReturn404() throws Exception {
        // Arrange
        String workflowName = "test-workflow";
        String version = "2.0.0";
        when(workflowService.getWorkflowDefinition(workflowName, version)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/workflows/name/{name}/version/{version}", workflowName, version))
                .andExpect(status().isNotFound());

        verify(workflowService).getWorkflowDefinition(workflowName, version);
    }

    @Test
    void createWorkflowDefinition_ShouldCreateNewWorkflow() throws Exception {
        // Arrange
        WorkflowDefinition newWorkflowDefinition = new WorkflowDefinition();
        newWorkflowDefinition.setName("new-workflow");
        newWorkflowDefinition.setDescription("New workflow description");
        newWorkflowDefinition.setStrategyType(WorkflowDefinition.ExecutionStrategyType.PARALLEL);

        when(workflowService.createWorkflowDefinition(any()))
                .thenReturn(mockWorkflowDefinition);

        // Act & Assert
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newWorkflowDefinition)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("test-workflow")));

        verify(workflowService).createWorkflowDefinition(any());
    }

    @Test
    void createWorkflowDefinition_WithInvalidData_ShouldReturn400() throws Exception {
        // Arrange - Create workflow with missing required fields
        WorkflowDefinition invalidWorkflow = new WorkflowDefinition();
        // Missing name and description (which are now required via @NotBlank annotations)
        invalidWorkflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        // Act & Assert
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidWorkflow)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Error")))
                .andExpect(jsonPath("$.fieldErrors", notNullValue()));

        verify(workflowService, never()).createWorkflowDefinition(any());
    }

    @Test
    void createWorkflowDefinition_WithEmptyName_ShouldReturn400() throws Exception {
        // Arrange - Create workflow with empty name
        WorkflowDefinition invalidWorkflow = new WorkflowDefinition();
        invalidWorkflow.setName(""); // Empty name should fail @NotBlank validation
        invalidWorkflow.setDescription("Valid description");
        invalidWorkflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        // Act & Assert
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidWorkflow)))
                .andExpect(status().isBadRequest());

        verify(workflowService, never()).createWorkflowDefinition(any());
    }

    @Test
    void updateWorkflowDefinition_ShouldUpdateExistingWorkflow() throws Exception {
        // Arrange
        Long workflowId = 1L;
        WorkflowDefinition updatedWorkflow = new WorkflowDefinition();
        updatedWorkflow.setName("updated-workflow");
        updatedWorkflow.setDescription("Updated description");
        updatedWorkflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.CONDITIONAL);

        when(workflowService.updateWorkflowDefinition(eq(workflowId), any()))
                .thenReturn(mockWorkflowDefinition);

        // Act & Assert
        mockMvc.perform(put("/api/workflows/{id}", workflowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedWorkflow)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)));

        verify(workflowService).updateWorkflowDefinition(eq(workflowId), any());
    }

    @Test
    void deleteWorkflowDefinition_ShouldDeleteWorkflow() throws Exception {
        // Arrange
        Long workflowId = 1L;
        doNothing().when(workflowService).deleteWorkflowDefinition(workflowId);

        // Act & Assert
        mockMvc.perform(delete("/api/workflows/{id}", workflowId))
                .andExpect(status().isNoContent());

        verify(workflowService).deleteWorkflowDefinition(workflowId);
    }

    @Test
    void createWorkflowDefinition_WithComplexWorkflow_ShouldCreateSuccessfully() throws Exception {
        // Arrange
        WorkflowDefinition complexWorkflow = new WorkflowDefinition();
        complexWorkflow.setName("complex-workflow");
        complexWorkflow.setDescription("Complex workflow with multiple tasks");
        complexWorkflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.CONDITIONAL);

        // Add multiple tasks with different configurations
        TaskDefinition task1 = new TaskDefinition();
        task1.setName("task-1");
        task1.setDescription("First task");
        task1.setType("rest-api");
        task1.setExecutionOrder(0);
        task1.setRetryLimit(3);
        task1.setRequireUserReview(true);
        task1.getConfiguration().put("url", "https://api.example.com/task1");
        task1.getConfiguration().put("method", "POST");
        complexWorkflow.getTasks().add(task1);

        TaskDefinition task2 = new TaskDefinition();
        task2.setName("task-2");
        task2.setDescription("Second task");
        task2.setType("rabbitmq");
        task2.setExecutionOrder(1);
        task2.getConfiguration().put("exchange", "test.exchange");
        task2.getConfiguration().put("routingKey", "test.routing.key");
        complexWorkflow.getTasks().add(task2);

        when(workflowService.createWorkflowDefinition(any()))
                .thenReturn(mockWorkflowDefinition);

        // Act & Assert
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complexWorkflow)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)));

        verify(workflowService).createWorkflowDefinition(any());
    }

    @Test
    void getAllWorkflowDefinitions_WhenEmpty_ShouldReturnEmptyList() throws Exception {
        // Arrange
        when(workflowService.getAllWorkflowDefinitions()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(workflowService).getAllWorkflowDefinitions();
    }

    @Test
    void getAllWorkflowDefinitions_ShouldReturnMultipleWorkflows() throws Exception {
        // Arrange
        WorkflowDefinition workflow2 = new WorkflowDefinition();
        workflow2.setId(2L);
        workflow2.setName("workflow-2");
        workflow2.setDescription("Second workflow");
        workflow2.setVersion("2.0.0");
        workflow2.setCreatedAt(LocalDateTime.now());
        workflow2.setStrategyType(WorkflowDefinition.ExecutionStrategyType.PARALLEL);

        List<WorkflowDefinition> workflowDefinitions = List.of(mockWorkflowDefinition, workflow2);
        when(workflowService.getAllWorkflowDefinitions()).thenReturn(workflowDefinitions);

        // Act & Assert
        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].name", is("test-workflow")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].name", is("workflow-2")));

        verify(workflowService).getAllWorkflowDefinitions();
    }
}
