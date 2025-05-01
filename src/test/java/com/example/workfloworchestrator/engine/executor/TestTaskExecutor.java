package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test task executor for integration testing
 * Can be configured to succeed, fail, or simulate different behaviors
 */
@Slf4j
@Component
public class TestTaskExecutor extends AbstractTaskExecutor {

    private static final String TASK_TYPE = "test-executor";

    // Track execution counts for testing
    private final AtomicInteger executionCount = new AtomicInteger(0);

    // Configuration keys
    private static final String CONFIG_SHOULD_FAIL = "shouldFail";
    private static final String CONFIG_FAIL_AFTER_ATTEMPTS = "failAfterAttempts";
    private static final String CONFIG_EXECUTION_DELAY_MS = "executionDelayMs";
    private static final String CONFIG_OUTPUT_KEY = "outputKey";
    private static final String CONFIG_OUTPUT_VALUE = "outputValue";

    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }

    @Override
    protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) throws Exception {
        int currentCount = executionCount.incrementAndGet();

        Map<String, String> config = taskDefinition.getConfiguration();

        // Simulate execution delay if configured
        String delayMs = config.get(CONFIG_EXECUTION_DELAY_MS);
        if (delayMs != null) {
            try {
                Thread.sleep(Long.parseLong(delayMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Check if task should fail
        if ("true".equals(config.get(CONFIG_SHOULD_FAIL))) {
            throw new TaskExecutionException("Test task configured to fail");
        }

        // Check if task should fail after certain attempts
        String failAfterAttempts = config.get(CONFIG_FAIL_AFTER_ATTEMPTS);
        if (failAfterAttempts != null) {
            int attempts = Integer.parseInt(failAfterAttempts);
            if (currentCount <= attempts) {
                throw new TaskExecutionException("Test task failing attempt " + currentCount + " of " + attempts);
            }
        }

        // Prepare success result
        Map<String, Object> result = new HashMap<>();
        result.put("executionCount", currentCount);
        result.put("taskName", taskDefinition.getName());

        // Add custom output if configured
        String outputKey = config.get(CONFIG_OUTPUT_KEY);
        String outputValue = config.get(CONFIG_OUTPUT_VALUE);

        if (outputKey != null && outputValue != null) {
            result.put(outputKey, outputValue);
        }

        // Pass through any input variables
        context.getAllVariables().forEach((key, value) ->
                result.put("input_" + key, value));

        return createSuccessResult(result);
    }

    /**
     * Reset execution count for testing
     */
    public void resetExecutionCount() {
        executionCount.set(0);
    }

    /**
     * Get current execution count
     */
    public int getExecutionCount() {
        return executionCount.get();
    }
}

