package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AbstractTaskExecutorTest {

    private TestTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new TestTaskExecutor();
    }

    @Test
    void shouldExecuteSuccessfully() throws Exception {
        // Given
        TaskDefinition taskDefinition = TaskDefinition.builder()
                .name("test-task")
                .configuration(Map.of("key", "value"))
                .build();
        ExecutionContext context = new ExecutionContext();

        // When
        Map<String, Object> result = executor.execute(taskDefinition, context);

        // Then
        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("taskName", "test-task");
    }

    @Test
    void shouldProcessVariables() {
        // Given
        ExecutionContext context = new ExecutionContext();
        context.setVariable("userName", "john");
        context.setVariable("userId", 123);

        String input = "Hello ${userName}, your ID is ${userId}";

        // When
        String result = executor.processVariables(input, context);

        // Then
        assertThat(result).isEqualTo("Hello john, your ID is 123");
    }

    @Test
    void shouldHandleNullVariables() {
        // Given
        ExecutionContext context = new ExecutionContext();
        String input = "Hello ${userName}";

        // When
        String result = executor.processVariables(input, context);

        // Then
        assertThat(result).isEqualTo("Hello ${userName}"); // Variable not replaced
    }

    @Test
    void shouldHandleEmptyInput() {
        // Given
        ExecutionContext context = new ExecutionContext();

        // When & Then
        assertThat(executor.processVariables(null, context)).isNull();
        assertThat(executor.processVariables("", context)).isEmpty();
    }

    @Test
    void shouldProcessConfigVariables() {
        // Given
        ExecutionContext context = new ExecutionContext();
        context.setVariable("host", "api.example.com");
        context.setVariable("port", "8080");

        Map<String, String> config = Map.of(
                "url", "https://${host}:${port}/api",
                "timeout", "30",
                "user", "${userName}" // This variable doesn't exist
        );

        // When
        Map<String, String> result = executor.processConfigVariables(config, context);

        // Then
        assertThat(result).containsEntry("url", "https://api.example.com:8080/api");
        assertThat(result).containsEntry("timeout", "30");
        assertThat(result).containsEntry("user", "${userName}"); // Unchanged
    }

    @Test
    void shouldCreateSuccessResult() {
        // When
        Map<String, Object> result = executor.createSuccessResult("test data");

        // Then
        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("data", "test data");
        assertThat(result).doesNotContainKey("errorMessage");
    }

    @Test
    void shouldCreateFailureResult() {
        // When
        Map<String, Object> result = executor.createFailureResult("Test error");

        // Then
        assertThat(result).containsEntry("success", false);
        assertThat(result).containsEntry("errorMessage", "Test error");
        assertThat(result).doesNotContainKey("data");
    }

    @Test
    void shouldHandleExecutionException() {
        // Given
        executor.setShouldThrowException(true);
        TaskDefinition taskDefinition = TaskDefinition.builder().name("test").build();
        ExecutionContext context = new ExecutionContext();

        // When & Then
        assertThatThrownBy(() -> executor.execute(taskDefinition, context))
                .isInstanceOf(TaskExecutionException.class)
                .hasMessageContaining("Test exception");
    }

    @Test
    void shouldWrapGenericExceptions() {
        // Given
        executor.setShouldThrowRuntimeException(true);
        TaskDefinition taskDefinition = TaskDefinition.builder().name("test").build();
        ExecutionContext context = new ExecutionContext();

        // When & Then
        assertThatThrownBy(() -> executor.execute(taskDefinition, context))
                .isInstanceOf(TaskExecutionException.class)
                .hasMessageContaining("Task execution failed");
    }

    @Test
    void shouldHandleNullConfig() {
        // Given
        ExecutionContext context = new ExecutionContext();

        // When
        Map<String, String> result = executor.processConfigVariables(null, context);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleNullContext() {
        // Given
        Map<String, String> config = Map.of("key", "value");

        // When
        Map<String, String> result = executor.processConfigVariables(config, null);

        // Then
        assertThat(result).isEmpty();
    }

    // Test implementation of AbstractTaskExecutor
    private static class TestTaskExecutor extends AbstractTaskExecutor {
        
        private boolean shouldThrowException = false;
        private boolean shouldThrowRuntimeException = false;

        @Override
        public String getTaskType() {
            return "test";
        }

        @Override
        protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) throws Exception {
            if (shouldThrowException) {
                throw new TaskExecutionException("Test exception");
            }
            if (shouldThrowRuntimeException) {
                throw new RuntimeException("Test runtime exception");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("taskName", taskDefinition.getName());
            return result;
        }

        public void setShouldThrowException(boolean shouldThrowException) {
            this.shouldThrowException = shouldThrowException;
        }

        public void setShouldThrowRuntimeException(boolean shouldThrowRuntimeException) {
            this.shouldThrowRuntimeException = shouldThrowRuntimeException;
        }
    }
}
