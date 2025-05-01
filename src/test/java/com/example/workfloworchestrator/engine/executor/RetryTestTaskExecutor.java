package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.engine.executor.AbstractTaskExecutor;
import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A variation that succeeds after a configurable number of retries
 */
@Slf4j
@Component
public class RetryTestTaskExecutor extends AbstractTaskExecutor {

    private static final String TASK_TYPE = "retry-test-executor";

    private final Map<String, AtomicInteger> executionCounts = new HashMap<>();

    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }

    @Override
    protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) throws Exception {
        String taskId = taskDefinition.getId().toString();
        AtomicInteger count = executionCounts.computeIfAbsent(taskId, k -> new AtomicInteger(0));

        int currentAttempt = count.incrementAndGet();
        int failUntilAttempt = Integer.parseInt(
                taskDefinition.getConfiguration().getOrDefault("failUntilAttempt", "2"));

        if (currentAttempt < failUntilAttempt) {
            log.info("Task {} failing on attempt {} (configured to fail until attempt {})",
                    taskDefinition.getName(), currentAttempt, failUntilAttempt);
            throw new TaskExecutionException("Simulated failure for retry testing");
        }

        log.info("Task {} succeeding on attempt {}", taskDefinition.getName(), currentAttempt);

        Map<String, Object> result = new HashMap<>();
        result.put("attempt", currentAttempt);
        result.put("success", true);

        return createSuccessResult(result);
    }

    public void resetExecutionCount(String taskId) {
        executionCounts.remove(taskId);
    }
}
