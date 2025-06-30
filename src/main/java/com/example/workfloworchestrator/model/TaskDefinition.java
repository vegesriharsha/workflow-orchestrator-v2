package com.example.workfloworchestrator.model;

import com.example.workfloworchestrator.json.model.TaskAttributeMapping;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entity representing a task definition in a workflow
 */
@Entity
@Table(name = "task_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDefinition {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "workflow_definition_id")
  private WorkflowDefinition workflowDefinition;

  @NotBlank(message = "Task name cannot be null or empty")
  @Size(max = 255, message = "Task name must not exceed 255 characters")
  @Column(nullable = false)
  private String name;

  @NotBlank(message = "Task type cannot be null or empty")
  @Size(max = 100, message = "Task type must not exceed 100 characters")
  @Column(nullable = false)
  private String type;

  @Min(value = 0, message = "Execution order cannot be negative")
  @Column(name = "execution_order")
  private Integer executionOrder;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "task_definition_config", joinColumns = @JoinColumn(name = "task_definition_id"))
  @MapKeyColumn(name = "config_key")
  @Column(name = "config_value", columnDefinition = "TEXT")
  @Builder.Default
  private Map<String, String> configuration = new HashMap<>();

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "task_definition_dependencies", joinColumns = @JoinColumn(name = "task_definition_id"))
  @Column(name = "dependency_name")
  private List<String> dependencies;

  @Column(name = "retry_enabled")
  @Builder.Default
  private boolean retryEnabled = false;

  @Min(value = 0, message = "Retry limit cannot be negative")
  @Max(value = 50, message = "Retry limit cannot exceed 50 attempts")
  @Column(name = "retry_limit")
  private Integer retryLimit;

  @Positive(message = "Timeout must be positive")
  @Max(value = 86400, message = "Timeout cannot exceed 24 hours (86400 seconds)")
  @Column(name = "timeout_seconds")
  private Integer timeoutSeconds;

  @Min(value = 0, message = "Retry delay cannot be negative")
  @Max(value = 3600, message = "Retry delay cannot exceed 1 hour (3600 seconds)")
  @Column(name = "retry_delay_seconds")
  @Builder.Default
  private long retryDelaySeconds = 5;

  @NotNull(message = "Execution mode is required")
  @Column(name = "execution_mode")
  @Enumerated(EnumType.STRING)
  private ExecutionMode executionMode = ExecutionMode.API;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Size(max = 2000, message = "Description must not exceed 2000 characters")
  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "require_user_review")
  private boolean requireUserReview = false;

  @Size(max = 1000, message = "Conditional expression must not exceed 1000 characters")
  @Column(name = "conditional_expression")
  private String conditionalExpression;

  @Column(name = "next_task_on_success")
  private Long nextTaskOnSuccess;

  @Column(name = "next_task_on_failure")
  private Long nextTaskOnFailure;

  @ElementCollection
  @CollectionTable(name = "task_dependencies",
      joinColumns = @JoinColumn(name = "task_definition_id"))
  @Column(name = "dependency_task_id")
  private List<Long> dependsOn = new ArrayList<>();

  @OneToMany(mappedBy = "taskDefinition", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
  @Valid
  @Builder.Default
  private List<TaskAttributeMapping> attributeMappings = new ArrayList<>();

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  /**
   * Check if this task has JSON attribute extraction configured
   * @return true if attribute mappings are defined for this task
   */
  @JsonIgnore
  public boolean hasAttributeExtractionConfigured() {
    return attributeMappings != null && !attributeMappings.isEmpty();
  }

  /**
   * Add an attribute mapping to this task definition
   * @param mapping the attribute mapping to add
   */
  public void addAttributeMapping(TaskAttributeMapping mapping) {
    if (attributeMappings == null) {
      attributeMappings = new ArrayList<>();
    }
    mapping.setTaskDefinition(this);
    attributeMappings.add(mapping);
  }

  /**
   * Remove an attribute mapping from this task definition
   * @param mapping the attribute mapping to remove
   */
  public void removeAttributeMapping(TaskAttributeMapping mapping) {
    if (attributeMappings != null) {
      attributeMappings.remove(mapping);
      mapping.setTaskDefinition(null);
    }
  }

  /**
   * Get all attribute mappings for this task
   * @return list of attribute mappings (never null)
   */
  public List<TaskAttributeMapping> getAttributeMappings() {
    return attributeMappings != null ? attributeMappings : new ArrayList<>();
  }

  /**
   * Get attribute mappings by HTTP location
   * @param location the HTTP location to filter by
   * @return list of attribute mappings for the specified location
   */
  public List<TaskAttributeMapping> getAttributeMappingsByLocation(TaskAttributeMapping.HttpLocation location) {
    return getAttributeMappings().stream()
        .filter(mapping -> mapping.getHttpLocation() == location)
        .toList();
  }

  /**
   * Get count of attribute mappings
   * @return number of attribute mappings configured
   */
  @JsonIgnore
  public int getAttributeMappingCount() {
    return attributeMappings != null ? attributeMappings.size() : 0;
  }

  /**
   * Get configuration value with variable substitution support
   * @param key the configuration key
   * @return the configuration value or null if not found
   */
  public String getConfigValue(String key) {
    return configuration != null ? configuration.get(key) : null;
  }

  /**
   * Set configuration value
   * @param key the configuration key
   * @param value the configuration value
   */
  public void setConfigValue(String key, String value) {
    if (configuration == null) {
      configuration = new HashMap<>();
    }
    configuration.put(key, value);
  }

  /**
   * Check if a configuration key exists
   * @param key the configuration key
   * @return true if the key exists
   */
  public boolean hasConfigKey(String key) {
    return configuration != null && configuration.containsKey(key);
  }

  /**
   * Get all configuration keys
   * @return set of configuration keys
   */
  @JsonIgnore
  public java.util.Set<String> getConfigKeys() {
    return configuration != null ? configuration.keySet() : java.util.Collections.emptySet();
  }

  /**
   * Check if task has dependencies
   * @return true if dependencies are defined
   */
  @JsonIgnore
  public boolean hasDependencies() {
    return dependencies != null && !dependencies.isEmpty();
  }

  /**
   * Check if retry is configured
   * @return true if retry is enabled and max attempts > 0
   */
  @JsonIgnore
  public boolean isRetryConfigured() {
    return retryEnabled && retryLimit != null && retryLimit > 0;
  }

  /**
   * Check if task supports the given task type
   * @param taskType the task type to check
   * @return true if this task definition supports the given type
   */
  public boolean supportsTaskType(String taskType) {
    return type != null && type.equalsIgnoreCase(taskType);
  }

  /**
   * Create a copy of this task definition with a new name
   * @param newName the new name for the task
   * @return a new TaskDefinition instance
   */
  public TaskDefinition copyWithName(String newName) {
    return TaskDefinition.builder()
        .name(newName)
        .type(this.type)
        .configuration(new HashMap<>(this.configuration))
        .dependencies(this.dependencies != null ? List.copyOf(this.dependencies) : null)
        .retryEnabled(this.retryEnabled)
        .retryLimit(this.retryLimit)
        .retryDelaySeconds(this.retryDelaySeconds)
        .timeoutSeconds(this.timeoutSeconds)
        .description(this.description)
        .build();
  }

  @Override
  public String toString() {
    return "TaskDefinition{" +
        "id=" + id +
        ", name='" + name + '\'' +
        ", type='" + type + '\'' +
        ", attributeMappingCount=" + getAttributeMappingCount() +
        ", retryEnabled=" + retryEnabled +
        ", retryLimit=" + retryLimit +
        ", timeoutSeconds=" + timeoutSeconds +
        '}';
  }
}
