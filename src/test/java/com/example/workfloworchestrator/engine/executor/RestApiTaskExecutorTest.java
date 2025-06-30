package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.JsonExtractionException;
import com.example.workfloworchestrator.json.model.RestApiRequest;
import com.example.workfloworchestrator.json.model.TaskAttributeMapping;
import com.example.workfloworchestrator.json.service.JsonAttributeExtractionService;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestApiTaskExecutorTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JsonAttributeExtractionService jsonAttributeExtractionService;

    private RestApiTaskExecutor executor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        executor = new RestApiTaskExecutor(restTemplate, objectMapper, jsonAttributeExtractionService);
    }

    @Test
    void shouldReturnCorrectTaskType() {
        assertThat(executor.getTaskType()).isEqualTo("rest-api");
    }

    @Test
    void shouldExecuteWithJsonExtraction() throws Exception {
        // Given
        TaskDefinition taskDefinition = createTaskDefinitionWithAttributeMappings();
        ExecutionContext context = createExecutionContextWithJsonData();
        
        // Create a proper attribute mapping for mocking
        TaskAttributeMapping mapping = TaskAttributeMapping.builder()
                .taskName("test-task")
                .sourcePath("/user/id")
                .targetField("id")
                .httpLocation(TaskAttributeMapping.HttpLocation.PATH_PARAM)
                .build();
        
        RestApiRequest extractedRequest = createExtractedRequest();
        when(jsonAttributeExtractionService.getMappingsForTask(taskDefinition.getName()))
                .thenReturn(List.of(mapping));
        when(jsonAttributeExtractionService.buildRequest(taskDefinition, context.getWorkflowData()))
                .thenReturn(extractedRequest);

        ResponseEntity<String> mockResponse = ResponseEntity.ok("{\"result\":\"success\"}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(mockResponse);

        // When
        Map<String, Object> result = executor.execute(taskDefinition, context);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("statusCode")).isEqualTo(200);
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("usedJsonExtraction")).isEqualTo(true);
        assertThat(result.get("extractedAttributeCount")).isEqualTo(4); // body + query + path + header
        
        verify(jsonAttributeExtractionService).getMappingsForTask(taskDefinition.getName());
        verify(jsonAttributeExtractionService).buildRequest(taskDefinition, context.getWorkflowData());
        verify(restTemplate).exchange(
                eq("https://api.example.com/users/123?status=active"), 
                eq(HttpMethod.GET), 
                any(), 
                eq(String.class)
        );
    }

    @Test
    void shouldExecuteWithTraditionalConfiguration() throws Exception {
        // Given
        TaskDefinition taskDefinition = createTaskDefinitionWithoutAttributeMappings();
        ExecutionContext context = createExecutionContext();

        when(jsonAttributeExtractionService.getMappingsForTask(taskDefinition.getName()))
                .thenReturn(List.of());

        ResponseEntity<String> mockResponse = ResponseEntity.ok("{\"result\":\"success\"}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(mockResponse);

        // When
        Map<String, Object> result = executor.execute(taskDefinition, context);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("statusCode")).isEqualTo(200);
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("usedJsonExtraction")).isEqualTo(false);
        
        verify(jsonAttributeExtractionService).getMappingsForTask(taskDefinition.getName());
        verifyNoMoreInteractions(jsonAttributeExtractionService);
        verify(restTemplate).exchange(
                eq("https://api.example.com/endpoint"), 
                eq(HttpMethod.POST), 
                any(), 
                eq(String.class)
        );
    }

    @Test
    void shouldBuildUrlWithPathParams() throws Exception {
        // Given
        TaskDefinition taskDefinition = createTaskDefinitionWithAttributeMappings();
        ExecutionContext context = createExecutionContextWithJsonData();
        
        TaskAttributeMapping mapping = TaskAttributeMapping.builder()
                .taskName("test-task")
                .sourcePath("/user/id")
                .targetField("id")
                .httpLocation(TaskAttributeMapping.HttpLocation.PATH_PARAM)
                .build();
        
        RestApiRequest extractedRequest = RestApiRequest.builder()
                .pathParams(Map.of("id", "123", "version", "v1"))
                .build();
        
        when(jsonAttributeExtractionService.getMappingsForTask(taskDefinition.getName()))
                .thenReturn(List.of(mapping));
        when(jsonAttributeExtractionService.buildRequest(taskDefinition, context.getWorkflowData()))
                .thenReturn(extractedRequest);

        ResponseEntity<String> mockResponse = ResponseEntity.ok("{}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(mockResponse);

        // When
        executor.execute(taskDefinition, context);

        // Then
        verify(restTemplate).exchange(
                eq("https://api.example.com/users/123/v1"), 
                eq(HttpMethod.GET), 
                any(), 
                eq(String.class)
        );
    }

    @Test
    void shouldBuildUrlWithQueryParams() throws Exception {
        // Given
        TaskDefinition taskDefinition = createTaskDefinitionWithAttributeMappings();
        ExecutionContext context = createExecutionContextWithJsonData();
        
        TaskAttributeMapping mapping = TaskAttributeMapping.builder()
                .taskName("test-task")
                .sourcePath("/user/status")
                .targetField("status")
                .httpLocation(TaskAttributeMapping.HttpLocation.QUERY_PARAM)
                .build();
        
        RestApiRequest extractedRequest = RestApiRequest.builder()
                .queryParams(Map.of("status", "active", "limit", "10"))
                .build();
        
        when(jsonAttributeExtractionService.getMappingsForTask(taskDefinition.getName()))
                .thenReturn(List.of(mapping));
        when(jsonAttributeExtractionService.buildRequest(taskDefinition, context.getWorkflowData()))
                .thenReturn(extractedRequest);

        ResponseEntity<String> mockResponse = ResponseEntity.ok("{}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(mockResponse);

        // When
        executor.execute(taskDefinition, context);

        // Then
        verify(restTemplate).exchange(
                argThat((String url) -> url.contains("status=active") && url.contains("limit=10")),
                eq(HttpMethod.GET), 
                any(), 
                eq(String.class)
        );
    }

    @Test
    void shouldAddExtractedHeaders() throws Exception {
        // Given
        TaskDefinition taskDefinition = createTaskDefinitionWithAttributeMappings();
        ExecutionContext context = createExecutionContextWithJsonData();
        
        TaskAttributeMapping mapping = TaskAttributeMapping.builder()
                .taskName("test-task")
                .sourcePath("/trace/id")
                .targetField("X-Trace-ID")
                .httpLocation(TaskAttributeMapping.HttpLocation.HEADER)
                .build();
        
        RestApiRequest extractedRequest = RestApiRequest.builder()
                .headers(Map.of("X-Trace-ID", "trace-123", "X-User-ID", "user-456"))
                .build();
        
        when(jsonAttributeExtractionService.getMappingsForTask(taskDefinition.getName()))
                .thenReturn(List.of(mapping));
        when(jsonAttributeExtractionService.buildRequest(taskDefinition, context.getWorkflowData()))
                .thenReturn(extractedRequest);

        ResponseEntity<String> mockResponse = ResponseEntity.ok("{}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(mockResponse);

        // When
        executor.execute(taskDefinition, context);

        // Then
        verify(restTemplate).exchange(
                anyString(), 
                eq(HttpMethod.GET), 
                argThat(httpEntity -> {
                    String traceId = httpEntity.getHeaders().getFirst("X-Trace-ID");
                    String userId = httpEntity.getHeaders().getFirst("X-User-ID");
                    return "trace-123".equals(traceId) && "user-456".equals(userId);
                }), 
                eq(String.class)
        );
    }

    @Test
    void shouldHandlePostRequestWithBody() throws Exception {
        // Given
        TaskDefinition taskDefinition = createTaskDefinitionWithAttributeMappings();
        taskDefinition.getConfiguration().put("method", "POST");
        ExecutionContext context = createExecutionContextWithJsonData();
        
        TaskAttributeMapping mapping = TaskAttributeMapping.builder()
                .taskName("test-task")
                .sourcePath("/user/profile/email")
                .targetField("email")
                .httpLocation(TaskAttributeMapping.HttpLocation.BODY)
                .build();
        
        JsonNode bodyNode = objectMapper.readTree("{\"name\":\"John\",\"email\":\"john@example.com\"}");
        RestApiRequest extractedRequest = RestApiRequest.builder()
                .body(bodyNode)
                .build();
        
        when(jsonAttributeExtractionService.getMappingsForTask(taskDefinition.getName()))
                .thenReturn(List.of(mapping));
        when(jsonAttributeExtractionService.buildRequest(taskDefinition, context.getWorkflowData()))
                .thenReturn(extractedRequest);

        ResponseEntity<String> mockResponse = ResponseEntity.ok("{}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(mockResponse);

        // When
        executor.execute(taskDefinition, context);

        // Then
        verify(restTemplate).exchange(
                anyString(), 
                eq(HttpMethod.POST), 
                argThat(httpEntity -> {
                    Object body = httpEntity.getBody();
                    if (body != null) {
                        String bodyStr = body.toString();
                        return bodyStr.contains("\"name\":\"John\"") && bodyStr.contains("\"email\":\"john@example.com\"");
                    }
                    return false;
                }), 
                eq(String.class)
        );
    }

    @Test
    void shouldHandleErrorResponse() throws Exception {
        // Given
        TaskDefinition taskDefinition = createTaskDefinitionWithoutAttributeMappings();
        ExecutionContext context = createExecutionContext();

        when(jsonAttributeExtractionService.getMappingsForTask(taskDefinition.getName()))
                .thenReturn(List.of());

        ResponseEntity<String> errorResponse = ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("{\"error\":\"Bad Request\"}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(errorResponse);

        // When
        Map<String, Object> result = executor.execute(taskDefinition, context);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("statusCode")).isEqualTo(400);
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("errorMessage")).isEqualTo("HTTP error: 400");
    }

    @Test
    void shouldValidateRequiredConfiguration() {
        // Given
        TaskDefinition taskDefinition = TaskDefinition.builder()
                .name("test-task")
                .configuration(Map.of("url", "https://api.example.com"))
                // Missing "method" configuration
                .build();
        ExecutionContext context = createExecutionContext();

        // When & Then
        assertThatThrownBy(() -> executor.execute(taskDefinition, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required configuration missing: method");
    }

    @Test
    void shouldHandleJsonExtractionException() throws Exception {
        // Given
        TaskDefinition taskDefinition = createTaskDefinitionWithAttributeMappings();
        ExecutionContext context = createExecutionContextWithJsonData();
        
        TaskAttributeMapping mapping = TaskAttributeMapping.builder()
                .taskName("test-task")
                .sourcePath("/user/id")
                .targetField("id")
                .httpLocation(TaskAttributeMapping.HttpLocation.PATH_PARAM)
                .build();
        
        when(jsonAttributeExtractionService.getMappingsForTask(taskDefinition.getName()))
                .thenReturn(List.of(mapping));
        when(jsonAttributeExtractionService.buildRequest(taskDefinition, context.getWorkflowData()))
                .thenThrow(new JsonExtractionException("Extraction failed"));

        // When & Then
        assertThatThrownBy(() -> executor.execute(taskDefinition, context))
                .isInstanceOf(JsonExtractionException.class)
                .hasMessage("Extraction failed");
    }

    @Test
    void shouldFallBackToConfigurationWhenNoWorkflowData() throws Exception {
        // Given
        TaskDefinition taskDefinition = createTaskDefinitionWithAttributeMappings();
        ExecutionContext context = createExecutionContext(); // No workflow data

        TaskAttributeMapping mapping = TaskAttributeMapping.builder()
                .taskName("test-task")
                .sourcePath("/user/id")
                .targetField("id")
                .httpLocation(TaskAttributeMapping.HttpLocation.PATH_PARAM)
                .build();

        when(jsonAttributeExtractionService.getMappingsForTask(taskDefinition.getName()))
                .thenReturn(List.of(mapping));

        ResponseEntity<String> mockResponse = ResponseEntity.ok("{}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(mockResponse);

        // When
        Map<String, Object> result = executor.execute(taskDefinition, context);

        // Then
        assertThat(result.get("usedJsonExtraction")).isEqualTo(false);
        verify(jsonAttributeExtractionService).getMappingsForTask(taskDefinition.getName());
        verifyNoMoreInteractions(jsonAttributeExtractionService);
    }

    @Test
    void shouldFallBackToConfigurationWhenNoAttributeMappings() throws Exception {
        // Given
        TaskDefinition taskDefinition = createTaskDefinitionWithAttributeMappings();
        ExecutionContext context = createExecutionContextWithJsonData();

        when(jsonAttributeExtractionService.getMappingsForTask(taskDefinition.getName()))
                .thenReturn(List.of()); // No mappings found

        ResponseEntity<String> mockResponse = ResponseEntity.ok("{}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(mockResponse);

        // When
        Map<String, Object> result = executor.execute(taskDefinition, context);

        // Then
        assertThat(result.get("usedJsonExtraction")).isEqualTo(false);
        verify(jsonAttributeExtractionService).getMappingsForTask(taskDefinition.getName());
        verifyNoMoreInteractions(jsonAttributeExtractionService);
    }

    @Test
    void shouldHandleExceptionWhenCheckingAttributeMappings() throws Exception {
        // Given
        TaskDefinition taskDefinition = createTaskDefinitionWithAttributeMappings();
        ExecutionContext context = createExecutionContextWithJsonData();

        when(jsonAttributeExtractionService.getMappingsForTask(taskDefinition.getName()))
                .thenThrow(new RuntimeException("Database error"));

        ResponseEntity<String> mockResponse = ResponseEntity.ok("{}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(mockResponse);

        // When
        Map<String, Object> result = executor.execute(taskDefinition, context);

        // Then
        assertThat(result.get("usedJsonExtraction")).isEqualTo(false);
        verify(jsonAttributeExtractionService).getMappingsForTask(taskDefinition.getName());
        verifyNoMoreInteractions(jsonAttributeExtractionService);
    }

    private TaskDefinition createTaskDefinitionWithAttributeMappings() {
        TaskDefinition taskDefinition = TaskDefinition.builder()
                .id(1L)
                .name("test-task")
                .attributeMappings(new ArrayList<>())
                .configuration(new HashMap<>())
                .build();

        // Add sample attribute mapping
        TaskAttributeMapping mapping = TaskAttributeMapping.builder()
                .sourcePath("/user/id")
                .targetField("id")
                .httpLocation(TaskAttributeMapping.HttpLocation.PATH_PARAM)
                .build();
        taskDefinition.getAttributeMappings().add(mapping);

        // Add configuration
        taskDefinition.getConfiguration().put("url", "https://api.example.com/users/{id}/{version}");
        taskDefinition.getConfiguration().put("method", "GET");

        return taskDefinition;
    }

    private TaskDefinition createTaskDefinitionWithoutAttributeMappings() {
        return TaskDefinition.builder()
                .id(1L)
                .name("test-task")
                .attributeMappings(new ArrayList<>())
                .configuration(Map.of(
                    "url", "https://api.example.com/endpoint",
                    "method", "POST",
                    "requestBody", "{\"test\":\"data\"}"
                ))
                .build();
    }

    private ExecutionContext createExecutionContext() {
        ExecutionContext context = new ExecutionContext();
        context.setVariable("headers", Map.of("Content-Type", "application/json"));
        return context;
    }

    private ExecutionContext createExecutionContextWithJsonData() throws Exception {
        ExecutionContext context = createExecutionContext();
        
        String workflowJsonString = """
            {
                "user": {
                    "id": 123,
                    "profile": {
                        "email": "john@example.com",
                        "status": "active"
                    }
                }
            }
            """;
        JsonNode workflowData = objectMapper.readTree(workflowJsonString);
        context.setWorkflowData(workflowData);
        
        return context;
    }

    private RestApiRequest createExtractedRequest() throws Exception {
        JsonNode body = objectMapper.readTree("{\"email\":\"john@example.com\"}");
        
        return RestApiRequest.builder()
                .body(body)
                .queryParams(Map.of("status", "active"))
                .pathParams(Map.of("id", "123"))
                .headers(Map.of("X-Extracted", "true"))
                .build();
    }
}
