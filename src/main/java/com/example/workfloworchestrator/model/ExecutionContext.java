package com.example.workfloworchestrator.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Execution context for workflow and task execution
 * Enhanced with JSON workflow data support for attribute extraction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class ExecutionContext {
    
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();
    
    // NEW: JSON workflow data for attribute extraction
    private JsonNode workflowData;
    
    // NEW: Current task name for context-aware operations
    private String currentTaskName;

    /**
     * Set a variable in the context
     * @param key the variable key
     * @param value the variable value
     */
    public void setVariable(String key, Object value) {
        if (variables == null) {
            variables = new HashMap<>();
        }
        variables.put(key, value);
    }

    /**
     * Get a variable from the context
     * @param key the variable key
     * @return the variable value or null if not found
     */
    public Object getVariable(String key) {
        return variables != null ? variables.get(key) : null;
    }

    /**
     * Get a typed variable from the context
     * @param key the variable key
     * @param type the expected type
     * @return the typed variable or null if not found or wrong type
     */
    public <T> T getVariable(String key, Class<T> type) {
        Object value = getVariable(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return type.cast(value);
        }
        return null;
    }

    /**
     * Check if a variable exists in the context
     * @param key the variable key
     * @return true if the variable exists
     */
    public boolean hasVariable(String key) {
        return variables != null && variables.containsKey(key);
    }

    /**
     * Remove a variable from the context
     * @param key the variable key
     */
    public void removeVariable(String key) {
        if (variables != null) {
            variables.remove(key);
        }
    }

    /**
     * Clear all variables from the context
     */
    public void clearVariables() {
        if (variables != null) {
            variables.clear();
        }
    }

    /**
     * Get a copy of all variables
     * @return copy of all variables map
     */
    public Map<String, Object> getAllVariables() {
        return variables != null ? new HashMap<>(variables) : new HashMap<>();
    }

    // NEW: JSON workflow data methods

    /**
     * Set the main workflow JSON data
     * @param data the JSON data
     */
    public void setWorkflowData(JsonNode data) {
        this.workflowData = data;
        // Also store as a variable for backward compatibility
        setVariable("_workflowData", data);
    }

    /**
     * Set workflow data from JSON string
     * @param jsonString the JSON string
     * @param objectMapper the JSON object mapper
     */
    public void setWorkflowData(String jsonString, ObjectMapper objectMapper) {
        try {
            JsonNode data = objectMapper.readTree(jsonString);
            setWorkflowData(data);
        } catch (Exception e) {
            log.error("Failed to parse workflow data JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JSON workflow data", e);
        }
    }

    /**
     * Check if workflow JSON data is available
     * @return true if workflow data is available
     */
    public boolean hasWorkflowData() {
        return workflowData != null && !workflowData.isNull();
    }

    /**
     * Get the workflow data as JsonNode
     * @return the workflow JSON data
     */
    public JsonNode getWorkflowData() {
        return workflowData;
    }

    /**
     * Set the current task name for context-aware operations
     * @param taskName the current task name
     */
    public void setCurrentTaskName(String taskName) {
        this.currentTaskName = taskName;
        setVariable("_currentTaskName", taskName);
    }

    /**
     * Get the current task name
     * @return the current task name
     */
    public String getCurrentTaskName() {
        return currentTaskName;
    }

    /**
     * Check if current task name is set
     * @return true if current task name is available
     */
    public boolean hasCurrentTaskName() {
        return currentTaskName != null && !currentTaskName.trim().isEmpty();
    }

    /**
     * Get a subset of workflow data for the current task
     * This can be used for task-specific data extraction
     * @return JsonNode subset or null if no workflow data
     */
    public JsonNode getTaskSubset() {
        if (!hasWorkflowData() || !hasCurrentTaskName()) {
            return workflowData;
        }
        
        // Try to find task-specific data under a path like "/tasks/{taskName}"
        JsonNode taskNode = workflowData.at("/tasks/" + currentTaskName);
        if (!taskNode.isMissingNode()) {
            return taskNode;
        }
        
        // Fall back to full workflow data
        return workflowData;
    }

    /**
     * Merge another context into this one
     * @param other the other context to merge
     */
    public void mergeFrom(ExecutionContext other) {
        if (other == null) {
            return;
        }
        
        // Merge variables
        if (other.variables != null) {
            if (this.variables == null) {
                this.variables = new HashMap<>();
            }
            this.variables.putAll(other.variables);
        }
        
        // Merge workflow data (other takes precedence)
        if (other.workflowData != null) {
            this.workflowData = other.workflowData;
        }
        
        // Merge current task name (other takes precedence)
        if (other.currentTaskName != null) {
            this.currentTaskName = other.currentTaskName;
        }
    }

    /**
     * Create a copy of this context
     * @return a new ExecutionContext with copied data
     */
    public ExecutionContext copy() {
        return ExecutionContext.builder()
                .variables(new HashMap<>(this.variables != null ? this.variables : new HashMap<>()))
                .workflowData(this.workflowData) // JsonNode is immutable, safe to share reference
                .currentTaskName(this.currentTaskName)
                .build();
    }

    /**
     * Create a context for a specific task
     * @param taskName the task name
     * @return a new context with the task name set
     */
    public ExecutionContext forTask(String taskName) {
        ExecutionContext taskContext = copy();
        taskContext.setCurrentTaskName(taskName);
        return taskContext;
    }

    @Override
    public String toString() {
        return "ExecutionContext{" +
                "variableCount=" + (variables != null ? variables.size() : 0) +
                ", hasWorkflowData=" + hasWorkflowData() +
                ", currentTaskName='" + currentTaskName + '\'' +
                '}';
    }
}
