package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * A task executor that simulates long-running operations
 */
@Slf4j
@Component
public class SlowTaskExecutor extends AbstractTaskExecutor {

    private static final String TASK_TYPE = "slow-executor";

    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }

    @Override
    protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) throws Exception {
        int durationMs = Integer.parseInt(
                taskDefinition.getConfiguration().getOrDefault("durationMs", "5000"));

        log.info("Starting slow task {} with duration {}ms", taskDefinition.getName(), durationMs);

        Thread.sleep(durationMs);

        log.info("Completed slow task {}", taskDefinition.getName());

        return createSuccessResult(Map.of("duration", durationMs));
    }
}
