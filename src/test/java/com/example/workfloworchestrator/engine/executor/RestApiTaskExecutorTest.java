package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestApiTaskExecutorTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private RestApiTaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        taskExecutor = new RestApiTaskExecutor(restTemplate, objectMapper);
    }

    @Test
    void getTaskType_ShouldReturnCorrectType() {
        assertThat(taskExecutor.getTaskType()).isEqualTo("rest-api");
    }

    @Test
    void execute_WithValidGetRequest_ShouldReturnSuccess() throws Exception {
        // Arrange
        TaskDefinition taskDefinition = new TaskDefinition();
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://api.example.com/data");
        config.put("method", "GET");
        taskDefinition.setConfiguration(config);

        ExecutionContext context = new ExecutionContext();

        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                "{\"status\":\"success\"}", HttpStatus.OK);

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(), eq(String.class)))
                .thenReturn(mockResponse);

        // Act
        Map<String, Object> result = taskExecutor.execute(taskDefinition, context);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("statusCode")).isEqualTo(200);
    }

    @Test
    void execute_WithPostRequest_ShouldIncludeRequestBody() throws Exception {
        // Arrange
        TaskDefinition taskDefinition = new TaskDefinition();
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://api.example.com/data");
        config.put("method", "POST");
        config.put("requestBody", "{\"key\":\"value\"}");
        taskDefinition.setConfiguration(config);

        ExecutionContext context = new ExecutionContext();

        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                "{\"id\":\"123\"}", HttpStatus.CREATED);

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        // Act
        Map<String, Object> result = taskExecutor.execute(taskDefinition, context);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("statusCode")).isEqualTo(201);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void execute_WithVariablesInConfig_ShouldProcessVariables() throws Exception {
        // Arrange
        TaskDefinition taskDefinition = new TaskDefinition();
        Map<String, String> config = new HashMap<>();
        config.put("url", "https://api.example.com/${endpoint}");
        config.put("method", "GET");
        taskDefinition.setConfiguration(config);

        ExecutionContext context = new ExecutionContext();
        context.setVariable("endpoint", "users");

        ResponseEntity<String> mockResponse = new ResponseEntity<>("[]", HttpStatus.OK);

        when(restTemplate.exchange(contains("users"), any(HttpMethod.class), any(), eq(String.class)))
                .thenReturn(mockResponse);

        // Act
        Map<String, Object> result = taskExecutor.execute(taskDefinition, context);

        // Assert
        assertThat(result).isNotNull();
        verify(restTemplate).exchange(eq("https://api.example.com/users"), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void execute_WithMissingRequiredConfig_ShouldThrowException() {
        // Arrange
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setConfiguration(new HashMap<>()); // Empty config

        ExecutionContext context = new ExecutionContext();

        // Act & Assert
        assertThatThrownBy(() -> taskExecutor.execute(taskDefinition, context))
                .isInstanceOf(TaskExecutionException.class)
                .getCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required task configuration parameter missing: url");
    }
}
