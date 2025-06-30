package com.example.workfloworchestrator.json.model;

import com.example.workfloworchestrator.model.TaskDefinition;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing attribute mapping configuration for JSON extraction
 * Defines how to extract specific attributes from main workflow JSON for individual tasks
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "task_attribute_mappings",
       indexes = {
           @Index(name = "idx_task_definition_id", columnList = "task_definition_id"),
           @Index(name = "idx_task_name", columnList = "task_name"),
           @Index(name = "idx_task_name_target_field", columnList = "task_name,target_field")
       })
public class TaskAttributeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_definition_id", nullable = false)
    private TaskDefinition taskDefinition;

    @NotBlank(message = "Task name is required")
    @Column(name = "task_name", nullable = false)
    private String taskName;

    @NotBlank(message = "Source path is required")
    @Column(name = "source_path", nullable = false, length = 500)
    private String sourcePath;

    @NotBlank(message = "Target field is required")
    @Column(name = "target_field", nullable = false)
    private String targetField;

    @NotNull(message = "HTTP location is required")
    @Column(name = "http_location", nullable = false)
    @Enumerated(EnumType.STRING)
    private HttpLocation httpLocation;

    @Column(name = "transformation_type")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransformationType transformationType = TransformationType.NONE;

    @Column(name = "transformation_config", columnDefinition = "TEXT")
    private String transformationConfig;

    @Column(name = "required")
    @Builder.Default
    private boolean required = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * HTTP location where the extracted attribute should be placed
     */
    public enum HttpLocation {
        BODY,
        QUERY_PARAM,
        PATH_PARAM,
        HEADER
    }

    /**
     * Type of transformation to apply to the extracted value
     */
    public enum TransformationType {
        NONE,
        DATE_FORMAT,
        VALUE_MAP,
        STRING_FORMAT
    }

    /**
     * Check if this mapping has transformation configured
     */
    public boolean hasTransformation() {
        return transformationType != null && transformationType != TransformationType.NONE;
    }

    /**
     * Get transformation type as string for service lookup
     */
    public String getTransformationType() {
        return transformationType != null ? transformationType.name() : TransformationType.NONE.name();
    }

    /**
     * Check if this is a required field
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Validate the mapping configuration
     */
    public void validate() {
        if (taskName == null || taskName.trim().isEmpty()) {
            throw new IllegalArgumentException("Task name cannot be null or empty");
        }
        if (sourcePath == null || sourcePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Source path cannot be null or empty");
        }
        if (targetField == null || targetField.trim().isEmpty()) {
            throw new IllegalArgumentException("Target field cannot be null or empty");
        }
        if (httpLocation == null) {
            throw new IllegalArgumentException("HTTP location cannot be null");
        }
        if (transformationType == null) {
            transformationType = TransformationType.NONE;
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        validate();
    }

    @PreUpdate
    protected void onUpdate() {
        syncTaskName();
        validate();
    }

    /**
     * Sync task name with task definition if available
     */
    private void syncTaskName() {
        if (taskDefinition != null && taskDefinition.getName() != null) {
            this.taskName = taskDefinition.getName();
        }
    }

    /**
     * Set the task definition and sync the task name
     * @param taskDefinition the task definition
     */
    public void setTaskDefinition(TaskDefinition taskDefinition) {
        this.taskDefinition = taskDefinition;
        syncTaskName();
    }

    @Override
    public String toString() {
        return "TaskAttributeMapping{" +
                "id=" + id +
                ", taskDefinitionId=" + (taskDefinition != null ? taskDefinition.getId() : null) +
                ", taskName='" + taskName + '\'' +
                ", sourcePath='" + sourcePath + '\'' +
                ", targetField='" + targetField + '\'' +
                ", httpLocation=" + httpLocation +
                ", transformationType=" + transformationType +
                ", required=" + required +
                '}';
    }
}
