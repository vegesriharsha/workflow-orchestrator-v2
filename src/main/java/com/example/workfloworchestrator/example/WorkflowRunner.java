package com.example.workfloworchestrator.example;

import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.UserReviewService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import com.example.workfloworchestrator.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@Slf4j
@Component
@RequiredArgsConstructor
class WorkflowRunner {

    private final WorkflowService workflowService;
    private final WorkflowExecutionService workflowExecutionService;
    private final UserReviewService userReviewService;
    private final TaskExecutionService taskExecutionService;

    public void runExample() {
        Scanner scanner = new Scanner(System.in);

        log.info("=== Workflow Orchestrator Example ===");
        log.info("Choose an example:");
        log.info("1. Simple Sequential Workflow");
        log.info("2. Workflow with User Review");
        log.info("3. Parallel Workflow");
        log.info("4. Conditional Workflow");
        log.info("5. Exit");

        System.out.print("Enter your choice (1-5): ");
        int choice = scanner.nextInt();

        switch (choice) {
            case 1:
                runSequentialWorkflow();
                break;
            case 2:
                runUserReviewWorkflow();
                break;
            case 3:
                runParallelWorkflow();
                break;
            case 4:
                runConditionalWorkflow();
                break;
            case 5:
                log.info("Exiting...");
                return;
            default:
                log.error("Invalid choice!");
        }
    }

    private void runSequentialWorkflow() {
        log.info("\n=== Running Sequential Workflow ===");

        // Create workflow definition
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("data-processing");
        workflow.setDescription("Process data through multiple steps");
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        // Task 1: Fetch Data
        TaskDefinition fetchTask = new TaskDefinition();
        fetchTask.setName("fetch-data");
        fetchTask.setDescription("fetch-data");
        fetchTask.setType("rest-api");
        fetchTask.setExecutionOrder(0);
        fetchTask.getConfiguration().put("url", "https://api.example.com/data");
        fetchTask.getConfiguration().put("method", "GET");

        // Task 2: Process Data
        TaskDefinition processTask = new TaskDefinition();
        processTask.setName("process-data");
        processTask.setDescription("process-data");
        processTask.setType("rest-api");
        processTask.setExecutionOrder(1);
        processTask.getConfiguration().put("url", "https://api.example.com/process");
        processTask.getConfiguration().put("method", "POST");

        // Task 3: Store Results
        TaskDefinition storeTask = new TaskDefinition();
        storeTask.setName("store-results");
        storeTask.setDescription("store-results");
        storeTask.setType("rest-api");
        storeTask.setExecutionOrder(2);
        storeTask.getConfiguration().put("url", "https://api.example.com/store");
        storeTask.getConfiguration().put("method", "POST");

        workflow.getTasks().add(fetchTask);
        workflow.getTasks().add(processTask);
        workflow.getTasks().add(storeTask);

        // Save and execute workflow
        WorkflowDefinition savedWorkflow = workflowService.createWorkflowDefinition(workflow);
        executeAndMonitorWorkflow(savedWorkflow.getName());
    }

    private void runUserReviewWorkflow() {
        log.info("\n=== Running User Review Workflow ===");

        // Create workflow definition
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("approval-process");
        workflow.setDescription("Process requiring manual approval");
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        // Task 1: Validate Request
        TaskDefinition validateTask = new TaskDefinition();
        validateTask.setName("validate-request");
        validateTask.setDescription("validate-request");
        validateTask.setType("rest-api");
        validateTask.setExecutionOrder(0);
        validateTask.getConfiguration().put("url", "https://api.example.com/validate");
        validateTask.getConfiguration().put("method", "POST");

        // Task 2: Manager Approval (User Review)
        TaskDefinition approvalTask = new TaskDefinition();
        approvalTask.setName("manager-approval");
        approvalTask.setDescription("manager-approval");
        approvalTask.setType("rest-api");
        approvalTask.setExecutionOrder(1);
        approvalTask.setRequireUserReview(true); // This task requires user approval
        approvalTask.getConfiguration().put("url", "https://api.example.com/approve");
        approvalTask.getConfiguration().put("method", "POST");

        // Task 3: Execute Approved Action
        TaskDefinition executeTask = new TaskDefinition();
        executeTask.setName("execute-action");
        executeTask.setDescription("execute-action");
        executeTask.setType("rest-api");
        executeTask.setExecutionOrder(2);
        executeTask.getConfiguration().put("url", "https://api.example.com/execute");
        executeTask.getConfiguration().put("method", "POST");

        workflow.getTasks().add(validateTask);
        workflow.getTasks().add(approvalTask);
        workflow.getTasks().add(executeTask);

        // Save and execute workflow
        WorkflowDefinition savedWorkflow = workflowService.createWorkflowDefinition(workflow);
        executeAndMonitorWorkflowWithReview(savedWorkflow.getName());
    }

    private void runParallelWorkflow() {
        log.info("\n=== Running Parallel Workflow ===");

        // Create workflow definition
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("parallel-processing");
        workflow.setDescription("Process multiple tasks in parallel");
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.PARALLEL);

        // Tasks with same execution order will run in parallel
        TaskDefinition task1 = new TaskDefinition();
        task1.setName("process-batch-1");
        task1.setDescription("process-batch-1");
        task1.setType("rest-api");
        task1.setExecutionOrder(0); // Same order - parallel execution
        task1.getConfiguration().put("url", "https://api.example.com/batch1");
        task1.getConfiguration().put("method", "POST");

        TaskDefinition task2 = new TaskDefinition();
        task2.setName("process-batch-2");
        task2.setDescription("process-batch-2");
        task2.setType("rest-api");
        task2.setExecutionOrder(0); // Same order - parallel execution
        task2.getConfiguration().put("url", "https://api.example.com/batch2");
        task2.getConfiguration().put("method", "POST");

        TaskDefinition task3 = new TaskDefinition();
        task3.setName("process-batch-3");
        task3.setDescription("process-batch-3");
        task3.setType("rest-api");
        task3.setExecutionOrder(0); // Same order - parallel execution
        task3.getConfiguration().put("url", "https://api.example.com/batch3");
        task3.getConfiguration().put("method", "POST");

        // Final task runs after all parallel tasks complete
        TaskDefinition finalTask = new TaskDefinition();
        finalTask.setName("aggregate-results");
        finalTask.setDescription("aggregate-results");
        finalTask.setType("rest-api");
        finalTask.setExecutionOrder(1); // Runs after parallel tasks
        finalTask.getConfiguration().put("url", "https://api.example.com/aggregate");
        finalTask.getConfiguration().put("method", "POST");

        workflow.getTasks().add(task1);
        workflow.getTasks().add(task2);
        workflow.getTasks().add(task3);
        workflow.getTasks().add(finalTask);

        // Save and execute workflow
        WorkflowDefinition savedWorkflow = workflowService.createWorkflowDefinition(workflow);
        executeAndMonitorWorkflow(savedWorkflow.getName());
    }

    private void runConditionalWorkflow() {
        log.info("\n=== Running Conditional Workflow ===");

        // Create workflow definition
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("conditional-routing");
        workflow.setDescription("Route tasks based on conditions");
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.CONDITIONAL);

        // Task 1: Evaluate Conditions
        TaskDefinition evaluateTask = new TaskDefinition();
        evaluateTask.setName("evaluate-conditions");
        evaluateTask.setType("rest-api");
        evaluateTask.setExecutionOrder(0);
        evaluateTask.getConfiguration().put("url", "https://api.example.com/evaluate");
        evaluateTask.getConfiguration().put("method", "POST");

        // Task 2: High Priority Path
        TaskDefinition highPriorityTask = new TaskDefinition();
        highPriorityTask.setName("high-priority-path");
        highPriorityTask.setType("rest-api");
        highPriorityTask.setExecutionOrder(1);
        highPriorityTask.setConditionalExpression("#priority == 'HIGH'");
        highPriorityTask.getConfiguration().put("url", "https://api.example.com/high-priority");
        highPriorityTask.getConfiguration().put("method", "POST");

        // Task 3: Normal Priority Path
        TaskDefinition normalPriorityTask = new TaskDefinition();
        normalPriorityTask.setName("normal-priority-path");
        normalPriorityTask.setType("rest-api");
        normalPriorityTask.setExecutionOrder(1);
        normalPriorityTask.setConditionalExpression("#priority == 'NORMAL'");
        normalPriorityTask.getConfiguration().put("url", "https://api.example.com/normal-priority");
        normalPriorityTask.getConfiguration().put("method", "POST");

        workflow.getTasks().add(evaluateTask);
        workflow.getTasks().add(highPriorityTask);
        workflow.getTasks().add(normalPriorityTask);

        // Save and execute workflow
        WorkflowDefinition savedWorkflow = workflowService.createWorkflowDefinition(workflow);
        executeAndMonitorWorkflow(savedWorkflow.getName());
    }

    private void executeAndMonitorWorkflow(String workflowName) {
        Map<String, String> variables = new HashMap<>();
        variables.put("input", "test-data");

        try {
            // Start workflow execution
            WorkflowExecution execution = workflowExecutionService.startWorkflow(
                    workflowName, null, variables);

            log.info("Started workflow '{}' with ID: {}", workflowName, execution.getId());
            log.info("Correlation ID: {}", execution.getCorrelationId());

            // Monitor workflow status
            boolean running = true;
            while (running) {
                Thread.sleep(1000); // Wait 1 second between checks

                WorkflowExecution currentExecution = workflowExecutionService.getWorkflowExecution(execution.getId());

                log.info("Current status: {}", currentExecution.getStatus());

                // Log task statuses
                var taskExecutions = taskExecutionService.getTaskExecutionsForWorkflow(execution.getId());
                for (TaskExecution taskExecution : taskExecutions) {
                    log.info("  Task '{}': {}",
                            taskExecution.getTaskDefinition().getName(),
                            taskExecution.getStatus());
                }

                // Check if workflow is complete
                switch (currentExecution.getStatus()) {
                    case COMPLETED:
                        log.info("Workflow completed successfully!");
                        running = false;
                        break;

                    case FAILED:
                        log.error("Workflow failed: {}", currentExecution.getErrorMessage());
                        running = false;
                        break;

                    case CANCELLED:
                        log.warn("Workflow was cancelled");
                        running = false;
                        break;

                    default:
                        // Still running
                        break;
                }
            }

        } catch (Exception e) {
            log.error("Error running workflow", e);
        }
    }

    private void executeAndMonitorWorkflowWithReview(String workflowName) {
        Map<String, String> variables = new HashMap<>();
        variables.put("requestId", "REQ-123");
        variables.put("amount", "5000");

        try {
            // Start workflow execution
            WorkflowExecution execution = workflowExecutionService.startWorkflow(
                    workflowName, null, variables);

            log.info("Started workflow '{}' with ID: {}", workflowName, execution.getId());

            // Monitor workflow status
            boolean running = true;
            while (running) {
                Thread.sleep(1000);

                WorkflowExecution currentExecution = workflowExecutionService.getWorkflowExecution(execution.getId());

                log.info("Current status: {}", currentExecution.getStatus());

                // Check if workflow needs user review
                if (currentExecution.getStatus() == WorkflowStatus.AWAITING_USER_REVIEW) {
                    log.info("Workflow is waiting for user review");

                    // Simulate user review
                    handleUserReview(currentExecution);

                    // Continue after review
                    continue;
                }

                // Check if workflow is complete
                switch (currentExecution.getStatus()) {
                    case COMPLETED:
                        log.info("Workflow completed successfully!");
                        running = false;
                        break;

                    case FAILED:
                        log.error("Workflow failed: {}", currentExecution.getErrorMessage());
                        running = false;
                        break;

                    default:
                        // Still running
                        break;
                }
            }

        } catch (Exception e) {
            log.error("Error running workflow", e);
        }
    }

    private void handleUserReview(WorkflowExecution execution) {
        Scanner scanner = new Scanner(System.in);

        // Find the pending review point
        var reviewPoints = userReviewService.getPendingReviewPoints();
        var reviewPoint = reviewPoints.stream()
                .filter(rp -> execution.getReviewPoints().contains(rp))
                .findFirst()
                .orElse(null);

        if (reviewPoint != null) {
            log.info("\n=== USER REVIEW REQUIRED ===");
            log.info("Review ID: {}", reviewPoint.getId());
            log.info("Task: {}", reviewPoint.getTaskExecutionId());

            System.out.println("\nChoose an action:");
            System.out.println("1. Approve");
            System.out.println("2. Reject");
            System.out.println("3. Restart Task");
            System.out.print("Enter your choice (1-3): ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            System.out.print("Enter comment: ");
            String comment = scanner.nextLine();

            UserReviewPoint.ReviewDecision decision;
            switch (choice) {
                case 1:
                    decision = UserReviewPoint.ReviewDecision.APPROVE;
                    break;
                case 2:
                    decision = UserReviewPoint.ReviewDecision.REJECT;
                    break;
                case 3:
                    decision = UserReviewPoint.ReviewDecision.RESTART;
                    break;
                default:
                    log.error("Invalid choice, defaulting to REJECT");
                    decision = UserReviewPoint.ReviewDecision.REJECT;
            }

            // Submit the review
            userReviewService.submitUserReview(reviewPoint.getId(), decision, "admin", comment);
            log.info("Review submitted: {}", decision);
        }
    }
}
