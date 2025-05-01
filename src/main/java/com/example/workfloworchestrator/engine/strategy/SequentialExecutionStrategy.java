package com.example.workfloworchestrator.engine.strategy;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sequential execution strategy
 * Executes tasks one after another in order
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SequentialExecutionStrategy implements ExecutionStrategy {

    private final TaskExecutionService taskExecutionService;
    private final WorkflowExecutionService workflowExecutionService;

    @Override
    @Transactional
    public CompletableFuture<WorkflowStatus> execute(WorkflowExecution workflowExecution) {
        CompletableFuture<WorkflowStatus> resultFuture = new CompletableFuture<>();

        try {
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();
            List<TaskDefinition> taskDefinitions = definition.getTasks();

            // Start from current task index (for resume/retry scenarios)
            int currentTaskIndex = workflowExecution.getCurrentTaskIndex() != null ?
                    workflowExecution.getCurrentTaskIndex() : 0;

            executeTasksSequentially(workflowExecution, taskDefinitions, currentTaskIndex, resultFuture);

        } catch (Exception e) {
            log.error("Error in sequential execution strategy", e);
            resultFuture.complete(WorkflowStatus.FAILED);
        }

        return resultFuture;
    }

    @Override
    @Transactional
    public CompletableFuture<WorkflowStatus> executeSubset(WorkflowExecution workflowExecution, List taskIds) {
        CompletableFuture<WorkflowStatus> resultFuture = new CompletableFuture<>();

        try {
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();

            // Filter tasks based on provided IDs and maintain order
            List<TaskDefinition> taskDefinitions = definition.getTasks().stream()
                    .filter(task -> taskIds.contains(task.getId()))
                    .sorted((task1, task2) -> task1.getExecutionOrder() - task2.getExecutionOrder())
                    .toList();

            if (taskDefinitions.isEmpty()) {
                resultFuture.complete(WorkflowStatus.COMPLETED);
                return resultFuture;
            }

            executeTasksSequentially(workflowExecution, taskDefinitions, 0, resultFuture);

        } catch (Exception e) {
            log.error("Error in sequential subset execution", e);
            resultFuture.complete(WorkflowStatus.FAILED);
        }

        return resultFuture;
    }

    private void executeTasksSequentially(WorkflowExecution workflowExecution,
                                          List<TaskDefinition> taskDefinitions,
                                          int startIndex,
                                          CompletableFuture<WorkflowStatus> resultFuture) {

        if (startIndex >= taskDefinitions.size()) {
            // All tasks completed
            resultFuture.complete(WorkflowStatus.COMPLETED);
            return;
        }

        // Get current task to execute
        TaskDefinition taskDefinition = taskDefinitions.get(startIndex);

        // Prepare inputs from workflow variables
        Map<String, String> inputs = new HashMap<>(workflowExecution.getVariables());

        // Create task execution
        TaskExecution taskExecution = taskExecutionService.createTaskExecution(
                workflowExecution, taskDefinition, inputs);

        // Update workflow's current task index
        workflowExecution.setCurrentTaskIndex(startIndex);
        workflowExecutionService.save(workflowExecution);

        // Check if task requires user review
        if (taskDefinition.isRequireUserReview()) {
            // Move workflow to await review
            workflowExecutionService.updateWorkflowExecutionStatus(
                    workflowExecution.getId(), WorkflowStatus.AWAITING_USER_REVIEW);

            // Create review point
            taskExecutionService.createUserReviewPoint(taskExecution.getId());

            // The workflow execution will be resumed when the review is submitted
            resultFuture.complete(WorkflowStatus.AWAITING_USER_REVIEW);
            return;
        }

        // Execute the task
        CompletableFuture<TaskExecution>  taskFuture = taskExecutionService.executeTask(taskExecution.getId());

        taskFuture.whenComplete((completedTask, throwable) -> {
            try {
                if (throwable != null) {
                    handleTaskFailure(workflowExecution, taskExecution, throwable, resultFuture);
                    return;
                }

                // Check task status
                if (completedTask.getStatus() == TaskStatus.COMPLETED) {
                    // Update workflow variables with task outputs
                    Map<String, String> variables = workflowExecution.getVariables();
                    variables.putAll(completedTask.getOutputs());
                    workflowExecution.setVariables(variables);
                    workflowExecutionService.save(workflowExecution);

                    // Move to next task
                    executeTasksSequentially(workflowExecution, taskDefinitions, startIndex + 1, resultFuture);
                } else if (completedTask.getStatus() == TaskStatus.FAILED) {
                    handleTaskFailure(workflowExecution, completedTask,
                            new TaskExecutionException(completedTask.getErrorMessage()), resultFuture);
                } else if (completedTask.getStatus() == TaskStatus.AWAITING_RETRY) {
                    // Task will be retried later, so we wait
                    resultFuture.complete(WorkflowStatus.RUNNING);
                } else {
                    // Other statuses like SKIPPED, CANCELLED, etc.
                    // Just continue with next task
                    executeTasksSequentially(workflowExecution, taskDefinitions, startIndex + 1, resultFuture);
                }
            } catch (Exception e) {
                log.error("Error processing task completion", e);
                resultFuture.complete(WorkflowStatus.FAILED);
            }
        });
    }

    private void handleTaskFailure(WorkflowExecution workflowExecution,
                                   TaskExecution taskExecution,
                                   Throwable throwable,
                                   CompletableFuture<WorkflowStatus> resultFuture) {

        log.error("Task execution failed", throwable);

        // Check if there's a next task on failure defined
        TaskDefinition taskDefinition = taskExecution.getTaskDefinition();
        Long nextTaskOnFailure = taskDefinition.getNextTaskOnFailure();

        if (nextTaskOnFailure != null) {
            // Find the index of the next task on failure
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();
            List<TaskDefinition> taskDefinitions = definition.getTasks();

            for (int i = 0; i < taskDefinitions.size(); i++) {
                if (taskDefinitions.get(i).getId().equals(nextTaskOnFailure)) {
                    // Update error message
                    workflowExecution.setErrorMessage("Task failed: " +
                            (throwable.getMessage() != null ? throwable.getMessage() : "Unknown error"));
                    workflowExecutionService.save(workflowExecution);

                    // Continue with the next task on failure
                    executeTasksSequentially(workflowExecution, taskDefinitions, i, resultFuture);
                    return;
                }
            }
        }

        // No next task on failure defined or not found, fail the workflow
        workflowExecution.setErrorMessage("Task failed: " +
                (throwable.getMessage() != null ? throwable.getMessage() : "Unknown error"));
        workflowExecutionService.save(workflowExecution);

        resultFuture.complete(WorkflowStatus.FAILED);
    }
}
