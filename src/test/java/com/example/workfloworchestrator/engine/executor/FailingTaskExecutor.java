package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * A variation that always fails - useful for testing retry logic
 */
@Slf4j
@Component
public class FailingTaskExecutor extends AbstractTaskExecutor {

    private static final String TASK_TYPE = "failing-executor";

    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }

    @Override
    protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) throws Exception {
        log.info("Executing failing task: {}", taskDefinition.getName());
        throw new TaskExecutionException("This task always fails for testing purposes");
    }
}
