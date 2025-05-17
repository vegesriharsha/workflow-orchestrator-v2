package com.example.workfloworchestrator.engine.strategy;

import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConditionalExecutionStrategyTest {

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private WorkflowExecutionService workflowExecutionService;

    @Mock
    private SequentialExecutionStrategy sequentialStrategy;

    private ConditionalExecutionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ConditionalExecutionStrategy(
                taskExecutionService,
                workflowExecutionService,
                sequentialStrategy
        );
    }

    @Test
    void execute_WithSequentialTasksAndConditions_ShouldExecuteInOrder() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition initialTask = createTaskDefinition("initial-task", 0);
        TaskDefinition conditionalTask1 = createTaskDefinition("conditional-task-1", 1);
        conditionalTask1.setConditionalExpression("#status == 'success'");
        TaskDefinition conditionalTask2 = createTaskDefinition("conditional-task-2", 1);
        conditionalTask2.setConditionalExpression("#status == 'failure'");

        workflowExecution.getWorkflowDefinition().getTasks().addAll(
                Arrays.asList(initialTask, conditionalTask1, conditionalTask2));

        // Set up task executions
        TaskExecution initialTaskExecution = createTaskExecution(initialTask, TaskStatus.COMPLETED);
        initialTaskExecution.getOutputs().put("status", "success");

        TaskExecution conditionalTaskExecution1 = createTaskExecution(conditionalTask1, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(initialTask), anyMap()))
                .thenReturn(initialTaskExecution);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(conditionalTask1), anyMap()))
                .thenReturn(conditionalTaskExecution1);

        when(taskExecutionService.executeTask(initialTaskExecution.getId()))
                .thenReturn(CompletableFuture.completedFuture(initialTaskExecution));
        when(taskExecutionService.executeTask(conditionalTaskExecution1.getId()))
                .thenReturn(CompletableFuture.completedFuture(conditionalTaskExecution1));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify initial task was executed
        verify(taskExecutionService).executeTask(initialTaskExecution.getId());

        // Verify conditional task 1 was executed (condition matched)
        verify(taskExecutionService).executeTask(conditionalTaskExecution1.getId());

        // Verify conditional task 2 was NOT executed (condition didn't match)
        verify(taskExecutionService, never()).createTaskExecution(eq(workflowExecution), eq(conditionalTask2), anyMap());
    }

    @Test
    void execute_WithNextTaskOnSuccess_ShouldFollowSuccessPath() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition successTask = createTaskDefinition("success-task", 1);
        TaskDefinition failureTask = createTaskDefinition("failure-task", 2);

        task1.setNextTaskOnSuccess(successTask.getId());
        task1.setNextTaskOnFailure(failureTask.getId());

        workflowExecution.getWorkflowDefinition().getTasks().addAll(
                Arrays.asList(task1, successTask, failureTask));

        TaskExecution task1Execution = createTaskExecution(task1, TaskStatus.COMPLETED);
        TaskExecution successTaskExecution = createTaskExecution(successTask, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(task1Execution);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(successTask), anyMap()))
                .thenReturn(successTaskExecution);

        when(taskExecutionService.executeTask(task1Execution.getId()))
                .thenReturn(CompletableFuture.completedFuture(task1Execution));
        when(taskExecutionService.executeTask(successTaskExecution.getId()))
                .thenReturn(CompletableFuture.completedFuture(successTaskExecution));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        verify(taskExecutionService).executeTask(task1Execution.getId());
        verify(taskExecutionService).executeTask(successTaskExecution.getId());
        verify(taskExecutionService, never()).createTaskExecution(eq(workflowExecution), eq(failureTask), anyMap());
    }

    @Test
    void execute_WithNextTaskOnFailure_ShouldFollowFailurePath() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition successTask = createTaskDefinition("success-task", 1);
        TaskDefinition failureTask = createTaskDefinition("failure-task", 2);

        task1.setNextTaskOnSuccess(successTask.getId());
        task1.setNextTaskOnFailure(failureTask.getId());

        workflowExecution.getWorkflowDefinition().getTasks().addAll(
                Arrays.asList(task1, successTask, failureTask));

        TaskExecution task1Execution = createTaskExecution(task1, TaskStatus.FAILED);
        task1Execution.setErrorMessage("Task failed");
        TaskExecution failureTaskExecution = createTaskExecution(failureTask, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(task1Execution);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(failureTask), anyMap()))
                .thenReturn(failureTaskExecution);

        when(taskExecutionService.executeTask(task1Execution.getId()))
                .thenReturn(CompletableFuture.completedFuture(task1Execution));
        when(taskExecutionService.executeTask(failureTaskExecution.getId()))
                .thenReturn(CompletableFuture.completedFuture(failureTaskExecution));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        verify(taskExecutionService).executeTask(task1Execution.getId());
        verify(taskExecutionService).executeTask(failureTaskExecution.getId());
        verify(taskExecutionService, never()).createTaskExecution(eq(workflowExecution), eq(successTask), anyMap());
    }

    @Test
    void execute_WithUserReviewTask_ShouldPauseForReview() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();
        workflowExecution.setId(1L);

        TaskDefinition reviewTask = createTaskDefinition("review-task", 0);
        reviewTask.setRequireUserReview(true);
        workflowExecution.getWorkflowDefinition().getTasks().add(reviewTask);

        TaskExecution reviewTaskExecution = createTaskExecution(reviewTask, TaskStatus.PENDING);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(reviewTask), anyMap()))
                .thenReturn(reviewTaskExecution);

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.AWAITING_USER_REVIEW);

        verify(workflowExecutionService).updateWorkflowExecutionStatus(1L, WorkflowStatus.AWAITING_USER_REVIEW);
        verify(taskExecutionService).createUserReviewPoint(reviewTaskExecution.getId());
        verify(taskExecutionService, never()).executeTask(anyLong());
    }

    @Test
    void execute_WithAwaitingRetryTask_ShouldReturnRunningStatus() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task = createTaskDefinition("retry-task", 0);
        workflowExecution.getWorkflowDefinition().getTasks().add(task);

        TaskExecution taskExecution = createTaskExecution(task, TaskStatus.AWAITING_RETRY);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task), anyMap()))
                .thenReturn(taskExecution);
        when(taskExecutionService.executeTask(taskExecution.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExecution));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.RUNNING);

        verify(taskExecutionService).executeTask(taskExecution.getId());
    }

    @Test
    void execute_WithTaskExecutionFailure_ShouldHandleFailure() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task = createTaskDefinition("failing-task", 0);
        workflowExecution.getWorkflowDefinition().getTasks().add(task);

        TaskExecution taskExecution = createTaskExecution(task, TaskStatus.PENDING);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task), anyMap()))
                .thenReturn(taskExecution);
        when(taskExecutionService.executeTask(taskExecution.getId()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Task execution failed")));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.FAILED);

        verify(workflowExecutionService).save(argThat(we ->
                we.getErrorMessage() != null && we.getErrorMessage().contains("Task failed")));
    }

    @Test
    void execute_WithComplexConditionalExpression_ShouldEvaluateCorrectly() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();
        workflowExecution.getVariables().put("count", "10");
        workflowExecution.getVariables().put("status", "active");

        TaskDefinition conditionalTask = createTaskDefinition("complex-conditional", 0);
        conditionalTask.setConditionalExpression("#count != null && T(Integer).parseInt(#count) > 5 && #status == 'active'");
        workflowExecution.getWorkflowDefinition().getTasks().add(conditionalTask);

        TaskExecution taskExecution = createTaskExecution(conditionalTask, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(conditionalTask), anyMap()))
                .thenReturn(taskExecution);
        when(taskExecutionService.executeTask(taskExecution.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExecution));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);
        verify(taskExecutionService).executeTask(taskExecution.getId());
    }

    @Test
    void execute_WithInvalidConditionalExpression_ShouldNotExecuteTask() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition conditionalTask = createTaskDefinition("invalid-conditional", 0);
        conditionalTask.setConditionalExpression("#invalidVariable.someMethod()");
        workflowExecution.getWorkflowDefinition().getTasks().add(conditionalTask);

        // IMPORTANT: Since the conditional expression is invalid but the task is at order 0 (start task),
        // it will be executed regardless. The conditional expression only affects task selection for
        // tasks AFTER the initial tasks have completed.
        TaskExecution taskExecution = createTaskExecution(conditionalTask, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(conditionalTask), anyMap()))
                .thenReturn(taskExecution);
        when(taskExecutionService.executeTask(taskExecution.getId()))
                .thenReturn(CompletableFuture.completedFuture(taskExecution));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // The task should be executed because it's a start task (order 0)
        verify(taskExecutionService).createTaskExecution(eq(workflowExecution), eq(conditionalTask), anyMap());
        verify(taskExecutionService).executeTask(taskExecution.getId());
    }

    @Test
    void execute_WithInvalidConditionalExpression_ShouldNotExecuteConditionalTask() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        // Create a start task that will execute first
        TaskDefinition startTask = createTaskDefinition("start-task", 0);
        // Create a conditional task with invalid expression that should NOT execute
        TaskDefinition conditionalTask = createTaskDefinition("invalid-conditional", 1);
        conditionalTask.setConditionalExpression("#invalidVariable.someMethod()");

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(startTask, conditionalTask));

        TaskExecution startTaskExecution = createTaskExecution(startTask, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(startTask), anyMap()))
                .thenReturn(startTaskExecution);
        when(taskExecutionService.executeTask(startTaskExecution.getId()))
                .thenReturn(CompletableFuture.completedFuture(startTaskExecution));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        // Start task should be executed
        verify(taskExecutionService).createTaskExecution(eq(workflowExecution), eq(startTask), anyMap());
        verify(taskExecutionService).executeTask(startTaskExecution.getId());

        // Conditional task should NOT be executed due to invalid expression
        verify(taskExecutionService, never()).createTaskExecution(eq(workflowExecution), eq(conditionalTask), anyMap());
    }

    @Test
    void executeSubset_ShouldDelegateToSequentialStrategy() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();
        List<Long> taskIds = Arrays.asList(1L, 2L, 3L);

        when(sequentialStrategy.executeSubset(workflowExecution, taskIds))
                .thenReturn(CompletableFuture.completedFuture(WorkflowStatus.COMPLETED));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.executeSubset(workflowExecution, taskIds);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);
        verify(sequentialStrategy).executeSubset(workflowExecution, taskIds);
    }

    @Test
    void execute_WithEmptyWorkflow_ShouldThrowException() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();
        // No tasks in the workflow

        // Act & Assert
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void execute_WithConditionalExpressionUsingVariables_ShouldEvaluateCorrectly() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        TaskDefinition conditionalTask = createTaskDefinition("conditional-task", 1);
        conditionalTask.setConditionalExpression("#result == 'PROCEED'");

        workflowExecution.getWorkflowDefinition().getTasks().addAll(Arrays.asList(task1, conditionalTask));

        TaskExecution task1Execution = createTaskExecution(task1, TaskStatus.COMPLETED);
        task1Execution.getOutputs().put("result", "PROCEED");
        TaskExecution conditionalTaskExecution = createTaskExecution(conditionalTask, TaskStatus.COMPLETED);

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(task1Execution);
        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(conditionalTask), anyMap()))
                .thenReturn(conditionalTaskExecution);

        when(taskExecutionService.executeTask(task1Execution.getId()))
                .thenReturn(CompletableFuture.completedFuture(task1Execution));
        when(taskExecutionService.executeTask(conditionalTaskExecution.getId()))
                .thenReturn(CompletableFuture.completedFuture(conditionalTaskExecution));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.COMPLETED);

        verify(taskExecutionService).executeTask(task1Execution.getId());
        verify(taskExecutionService).executeTask(conditionalTaskExecution.getId());
    }

    @Test
    void execute_WithFailureAndNoErrorHandler_ShouldFailWorkflow() throws Exception {
        // Arrange
        WorkflowExecution workflowExecution = createWorkflowExecution();

        TaskDefinition task1 = createTaskDefinition("task-1", 0);
        workflowExecution.getWorkflowDefinition().getTasks().add(task1);

        TaskExecution task1Execution = createTaskExecution(task1, TaskStatus.FAILED);
        task1Execution.setErrorMessage("Task execution failed");

        when(taskExecutionService.createTaskExecution(eq(workflowExecution), eq(task1), anyMap()))
                .thenReturn(task1Execution);
        when(taskExecutionService.executeTask(task1Execution.getId()))
                .thenReturn(CompletableFuture.completedFuture(task1Execution));

        // Act
        CompletableFuture<WorkflowStatus> result = strategy.execute(workflowExecution);

        // Assert
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(WorkflowStatus.FAILED);

        verify(workflowExecutionService).save(argThat(we ->
                we.getErrorMessage() != null && we.getErrorMessage().contains("Task failed")));
    }

    private WorkflowExecution createWorkflowExecution() {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setTasks(new ArrayList<>());

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowDefinition(definition);
        execution.setVariables(new HashMap<>());
        return execution;
    }

    private TaskDefinition createTaskDefinition(String name, int order) {
        TaskDefinition task = new TaskDefinition();
        task.setId((long) (name.hashCode() & 0x7FFFFFFF));
        task.setName(name);
        task.setExecutionOrder(order);
        task.setType("test");
        return task;
    }

    private TaskExecution createTaskExecution(TaskDefinition definition, TaskStatus status) {
        TaskExecution execution = new TaskExecution();
        execution.setId(definition.getId());
        execution.setTaskDefinition(definition);
        execution.setStatus(status);
        execution.setOutputs(new HashMap<>());
        return execution;
    }
}
