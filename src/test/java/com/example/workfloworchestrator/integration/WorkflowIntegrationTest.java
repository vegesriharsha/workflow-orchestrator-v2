package com.example.workfloworchestrator.integration;

import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import com.example.workfloworchestrator.service.WorkflowService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkflowIntegrationTest {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowExecutionService workflowExecutionService;

    @Autowired
    private TaskExecutionService taskExecutionService;

    private WorkflowDefinition testWorkflow;

    @Autowired
    private EntityManager entityManager;

    // Mock RabbitMQ components to avoid connection issues in tests
    @MockBean
    private org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory;

    @MockBean
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @MockBean
    private com.example.workfloworchestrator.messaging.RabbitMQSender rabbitMQSender;

    @MockBean
    private com.example.workfloworchestrator.messaging.RabbitMQReceiver rabbitMQReceiver;

    @BeforeEach
    void setUp() {
        // Create a test workflow definition
        testWorkflow = new WorkflowDefinition();
        testWorkflow.setName("integration-test-workflow");
        testWorkflow.setDescription("Test workflow for integration testing");
        testWorkflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        // Add test tasks
        TaskDefinition task1 = new TaskDefinition();
        task1.setName("test-task-1");
        task1.setDescription("test-task-1");
        task1.setType("test-executor");
        task1.setExecutionOrder(0);
        task1.setExecutionMode(ExecutionMode.API);

        TaskDefinition task2 = new TaskDefinition();
        task2.setName("test-task-2");
        task2.setDescription("test-task-2");
        task2.setType("test-executor");
        task2.setExecutionOrder(1);
        task2.setExecutionMode(ExecutionMode.API);

        testWorkflow.getTasks().add(task1);
        testWorkflow.getTasks().add(task2);

        testWorkflow = workflowService.createWorkflowDefinition(testWorkflow);
    }

    @Test
    void shouldExecuteSequentialWorkflowSuccessfully() throws Exception {
        // Arrange
        Map<String, String> variables = new HashMap<>();
        variables.put("testKey", "testValue");

        // Act
        WorkflowExecution execution = workflowExecutionService.startWorkflow(
                testWorkflow.getName(), null, variables);

        // Force synchronization with the database
        entityManager.flush();
        entityManager.clear();

        // Wait for workflow to complete (simplified for testing)
        awaitWorkflowCompletion(execution.getId(), 30);

        // Assert
        WorkflowExecution completedExecution = workflowExecutionService.getWorkflowExecution(execution.getId());
        assertThat(completedExecution.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        List<TaskExecution> taskExecutions = taskExecutionService.getTaskExecutionsForWorkflow(execution.getId());
        assertThat(taskExecutions).hasSize(2);
        assertThat(taskExecutions).allMatch(task -> task.getStatus() == TaskStatus.COMPLETED);
    }

    @Test
    void shouldHandleWorkflowPauseAndResume() throws Exception {
        // Arrange
        Map<String, String> variables = new HashMap<>();

        // Act - Start workflow
        WorkflowExecution execution = workflowExecutionService.startWorkflow(
                testWorkflow.getName(), null, variables);

        // Pause workflow
        WorkflowExecution pausedExecution = workflowExecutionService.pauseWorkflowExecution(execution.getId());
        assertThat(pausedExecution.getStatus()).isEqualTo(WorkflowStatus.PAUSED);

        // Resume workflow
        WorkflowExecution resumedExecution = workflowExecutionService.resumeWorkflowExecution(execution.getId());
        assertThat(resumedExecution.getStatus()).isEqualTo(WorkflowStatus.RUNNING);

        // Wait for completion
        awaitWorkflowCompletion(execution.getId(), 10);

        // Assert final state
        WorkflowExecution finalExecution = workflowExecutionService.getWorkflowExecution(execution.getId());
        assertThat(finalExecution.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void shouldHandleTaskFailureAndRetry() throws Exception {
        // Arrange - Create workflow with retry task
        TaskDefinition retryTask = new TaskDefinition();
        retryTask.setName("retry-task");
        retryTask.setDescription("retry-task");
        retryTask.setType("failing-executor"); // Custom executor that fails initially
        retryTask.setExecutionOrder(0);
        retryTask.setRetryLimit(3);
        retryTask.setExecutionMode(ExecutionMode.API);

        WorkflowDefinition retryWorkflow = new WorkflowDefinition();
        retryWorkflow.setName("retry-test-workflow");
        retryWorkflow.setDescription("retry-test-workflow");
        retryWorkflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);
        retryWorkflow.getTasks().add(retryTask);

        retryWorkflow = workflowService.createWorkflowDefinition(retryWorkflow);

        // Act
        WorkflowExecution execution = workflowExecutionService.startWorkflow(
                retryWorkflow.getName(), null, new HashMap<>());

        // Wait for workflow to handle retries
        awaitWorkflowCompletion(execution.getId(), 20);

        // Assert
        List<TaskExecution> taskExecutions = taskExecutionService.getTaskExecutionsForWorkflow(execution.getId());
        assertThat(taskExecutions).isNotEmpty();

        TaskExecution retriedTask = taskExecutions.get(0);
        assertThat(retriedTask.getRetryCount()).isGreaterThan(0);
    }

    private void awaitWorkflowCompletion(Long workflowExecutionId, int timeoutSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            WorkflowExecution execution = workflowExecutionService.getWorkflowExecution(workflowExecutionId);

            if (execution.getStatus() == WorkflowStatus.COMPLETED ||
                    execution.getStatus() == WorkflowStatus.FAILED) {
                return;
            }

            TimeUnit.MILLISECONDS.sleep(500);
        }
        throw new AssertionError("Workflow did not complete within " + timeoutSeconds + " seconds");
    }
}
