package com.example.workfloworchestrator.service;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.repository.WorkflowExecutionRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WorkflowExecutionService {

    private final WorkflowService workflowService;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private WorkflowEngine workflowEngine;
    private final EventPublisherService eventPublisherService;

    /**
     * Constructor with fields except WorkflowEngine
     *
     * @param workflowService the workflow service
     * @param workflowExecutionRepository the repository
     * @param eventPublisherService the event publisher
     */
    @Autowired
    public WorkflowExecutionService(
            WorkflowService workflowService,
            WorkflowExecutionRepository workflowExecutionRepository,
            EventPublisherService eventPublisherService) {
        this.workflowService = workflowService;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.eventPublisherService = eventPublisherService;
    }

    /**
     * Setter for WorkflowEngine - used to break circular dependency
     *
     * @param workflowEngine the workflow engine
     */
    @Autowired
    public void setWorkflowEngine(@Lazy WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    @Transactional
    public WorkflowExecution startWorkflow(String workflowName, String version, Map<String, String> variables) {
        WorkflowDefinition workflowDefinition = getWorkflowDefinition(workflowName, version);

        // Create workflow execution
        WorkflowExecution execution = createWorkflowExecution(workflowDefinition, variables);

        // Get the execution ID to use in the lambda
        final Long executionId = execution.getId();

        // Register a synchronization to execute after the transaction commits
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                workflowEngine.executeWorkflow(executionId);
            }
        });

        return execution;
    }

    @Transactional
    public WorkflowExecution createWorkflowExecution(WorkflowDefinition workflowDefinition, Map<String, String> variables) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowDefinition(workflowDefinition);
        execution.setCorrelationId(UUID.randomUUID().toString());
        execution.setStatus(WorkflowStatus.CREATED);
        execution.setStartedAt(LocalDateTime.now());
        execution.setCurrentTaskIndex(0);
        execution.setRetryCount(0);

        if (variables != null) {
            execution.setVariables(variables);
        }

        return workflowExecutionRepository.saveAndFlush(execution);
    }

    WorkflowDefinition getWorkflowDefinition(String name, String version) {
        if (version != null && !version.isEmpty()) {
            return workflowService.getWorkflowDefinition(name, version)
                    .orElseThrow(() -> new WorkflowException("Workflow definition not found with name: " + name + " and version: " + version));
        } else {
            return workflowService.getLatestWorkflowDefinition(name)
                    .orElseThrow(() -> new WorkflowException("Workflow definition not found with name: " + name));
        }
    }

    @Transactional
    public WorkflowExecution getWorkflowExecution(Long id) {
        return workflowExecutionRepository.findById(id)
                .orElseThrow(() -> new WorkflowException("Workflow execution not found with id: " + id));
    }

    @Transactional
    public WorkflowExecution getWorkflowExecutionByCorrelationId(String correlationId) {
        return workflowExecutionRepository.findByCorrelationId(correlationId)
                .orElseThrow(() -> new WorkflowException("Workflow execution not found with correlationId: " + correlationId));
    }

    @Transactional
    public WorkflowExecution updateWorkflowExecutionStatus(Long id, WorkflowStatus status) {
        WorkflowExecution execution = getWorkflowExecution(id);
        execution.setStatus(status);

        if (status == WorkflowStatus.COMPLETED || status == WorkflowStatus.FAILED) {
            execution.setCompletedAt(LocalDateTime.now());
        }

        // Publish event for status change
        eventPublisherService.publishWorkflowStatusChangedEvent(execution);

        return workflowExecutionRepository.save(execution);
    }

    @Transactional
    public WorkflowExecution pauseWorkflowExecution(Long id) {
        WorkflowExecution execution = getWorkflowExecution(id);

        if (execution.getStatus() == WorkflowStatus.RUNNING) {
            execution.setStatus(WorkflowStatus.PAUSED);
            workflowExecutionRepository.save(execution);

            eventPublisherService.publishWorkflowPausedEvent(execution);
        }

        return execution;
    }

    @Transactional
    public WorkflowExecution resumeWorkflowExecution(Long id) {
        WorkflowExecution execution = getWorkflowExecution(id);

        if (execution.getStatus() == WorkflowStatus.PAUSED) {
            execution.setStatus(WorkflowStatus.RUNNING);
            workflowExecutionRepository.save(execution);

            // Continue execution
            workflowEngine.executeWorkflow(id);

            eventPublisherService.publishWorkflowResumedEvent(execution);
        }

        return execution;
    }

    @Transactional
    public WorkflowExecution cancelWorkflowExecution(Long id) {
        WorkflowExecution execution = getWorkflowExecution(id);

        if (execution.getStatus() != WorkflowStatus.COMPLETED &&
                execution.getStatus() != WorkflowStatus.FAILED &&
                execution.getStatus() != WorkflowStatus.CANCELLED) {

            execution.setStatus(WorkflowStatus.CANCELLED);
            execution.setCompletedAt(LocalDateTime.now());
            workflowExecutionRepository.save(execution);

            eventPublisherService.publishWorkflowCancelledEvent(execution);
        }

        return execution;
    }

    @Transactional
    public WorkflowExecution retryWorkflowExecution(Long id) {
        WorkflowExecution execution = getWorkflowExecution(id);

        if (execution.getStatus() == WorkflowStatus.FAILED) {
            execution.setStatus(WorkflowStatus.RUNNING);
            execution.setRetryCount(execution.getRetryCount() + 1);
            workflowExecutionRepository.save(execution);

            // Continue execution from failed task
            workflowEngine.executeWorkflow(id);

            eventPublisherService.publishWorkflowRetryEvent(execution);
        }

        return execution;
    }

    @Transactional(readOnly = true)
    public List<WorkflowExecution> getWorkflowExecutionsByStatus(WorkflowStatus status) {
        return workflowExecutionRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<WorkflowExecution> getStuckWorkflowExecutions(LocalDateTime before) {
        return workflowExecutionRepository.findStuckExecutions(WorkflowStatus.RUNNING, before);
    }

    @Transactional
    public WorkflowExecution save(WorkflowExecution workflowExecution) {
        return workflowExecutionRepository.save(workflowExecution);
    }

    /**
     * Find completed/failed/cancelled workflows older than a specified date
     * Used for cleanup operations
     *
     * @param before the date threshold
     * @return list of old workflow executions
     */
    @Transactional(readOnly = true)
    public List<WorkflowExecution> findCompletedWorkflowsOlderThan(LocalDateTime before) {
        // Find workflows with terminal statuses that completed before the threshold
        List<WorkflowExecution> completedWorkflows = workflowExecutionRepository.findByStatusIn(
                List.of(WorkflowStatus.COMPLETED, WorkflowStatus.FAILED, WorkflowStatus.CANCELLED));

        // Filter for those completed before the threshold date
        return completedWorkflows.stream()
                .filter(we -> we.getCompletedAt() != null && we.getCompletedAt().isBefore(before))
                .collect(Collectors.toList());
    }

    /**
     * Find workflows that have been paused longer than a specified duration
     * Used for monitoring potentially forgotten workflows
     *
     * @param before the date threshold
     * @return list of long-paused workflow executions
     */
    @Transactional(readOnly = true)
    public List<WorkflowExecution> findPausedWorkflowsOlderThan(LocalDateTime before) {
        List<WorkflowExecution> pausedWorkflows = workflowExecutionRepository.findByStatus(WorkflowStatus.PAUSED);

        // Filter for workflows paused before the threshold date
        // For paused workflows, we use startedAt since there's no specific "pausedAt" timestamp
        return pausedWorkflows.stream()
                .filter(we -> we.getStartedAt() != null && we.getStartedAt().isBefore(before))
                .collect(Collectors.toList());
    }

    /**
     * Delete a workflow execution and all associated task executions and review points
     * Used for cleanup operations
     *
     * @param workflowExecutionId the workflow execution ID
     */
    @Transactional
    public void deleteWorkflowExecution(Long workflowExecutionId) {
        WorkflowExecution workflowExecution = getWorkflowExecution(workflowExecutionId);

        // Only allow deletion of terminated workflows
        if (workflowExecution.getStatus() != WorkflowStatus.COMPLETED &&
                workflowExecution.getStatus() != WorkflowStatus.FAILED &&
                workflowExecution.getStatus() != WorkflowStatus.CANCELLED) {

            throw new WorkflowException("Cannot delete workflow that is not in a terminal state: " +
                    workflowExecution.getStatus());
        }

        // Delete the workflow execution
        // Task executions, review points, and variables should be deleted by cascade
        workflowExecutionRepository.deleteById(workflowExecutionId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowExecution> getAllWorkflowExecutions() {
        return workflowExecutionRepository.findAll();
    }

    @Transactional
    public WorkflowExecution retryWorkflowExecutionSubset(Long workflowExecutionId, List<Long> taskIds) {
        var workflowExecution = getWorkflowExecution(workflowExecutionId);

        if (workflowExecution.getStatus() == WorkflowStatus.FAILED ||
                workflowExecution.getStatus() == WorkflowStatus.PAUSED) {

            workflowExecution.setStatus(WorkflowStatus.RUNNING);
            workflowExecution.setRetryCount(workflowExecution.getRetryCount() + 1);
            workflowExecutionRepository.save(workflowExecution);

            // Execute subset of tasks
            workflowEngine.executeTaskSubset(workflowExecutionId, taskIds);

            eventPublisherService.publishWorkflowRetryEvent(workflowExecution);
        }

        return workflowExecution;
    }
}
