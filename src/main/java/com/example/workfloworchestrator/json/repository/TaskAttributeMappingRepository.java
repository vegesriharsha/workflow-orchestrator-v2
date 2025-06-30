package com.example.workfloworchestrator.json.repository;

import com.example.workfloworchestrator.json.model.TaskAttributeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for TaskAttributeMapping entities
 */
@Repository
public interface TaskAttributeMappingRepository extends JpaRepository<TaskAttributeMapping, Long> {

    /**
     * Find all attribute mappings for a specific task name
     */
    List<TaskAttributeMapping> findByTaskName(String taskName);

    /**
     * Find all attribute mappings for a specific task name ordered by HTTP location
     */
    List<TaskAttributeMapping> findByTaskNameOrderByHttpLocationAsc(String taskName);

    /**
     * Find all mappings for a specific HTTP location
     */
    List<TaskAttributeMapping> findByHttpLocation(TaskAttributeMapping.HttpLocation httpLocation);

    /**
     * Find required mappings for a task name
     */
    @Query("SELECT tam FROM TaskAttributeMapping tam WHERE tam.taskName = :taskName AND tam.required = true")
    List<TaskAttributeMapping> findRequiredMappings(@Param("taskName") String taskName);

    /**
     * Check if a task has any attribute mappings configured
     */
    boolean existsByTaskName(String taskName);

    /**
     * Count the number of mappings for a task
     */
    long countByTaskName(String taskName);

    /**
     * Find mappings that require transformation for a task
     */
    @Query("SELECT tam FROM TaskAttributeMapping tam WHERE tam.taskName = :taskName AND tam.transformationType != 'NONE'")
    List<TaskAttributeMapping> findMappingsRequiringTransformation(@Param("taskName") String taskName);

    /**
     * Delete all mappings for a task name
     */
    void deleteByTaskName(String taskName);

    /**
     * Find mappings by task name and HTTP location
     */
    List<TaskAttributeMapping> findByTaskNameAndHttpLocation(String taskName, TaskAttributeMapping.HttpLocation httpLocation);

    /**
     * Find mappings by task name and target field
     */
    TaskAttributeMapping findByTaskNameAndTargetField(String taskName, String targetField);

    /**
     * Find all distinct task names that have attribute mappings
     */
    @Query("SELECT DISTINCT tam.taskName FROM TaskAttributeMapping tam")
    List<String> findDistinctTaskNames();

    /**
     * Find all attribute mappings for a specific task definition ID
     */
    List<TaskAttributeMapping> findByTaskDefinitionId(Long taskDefinitionId);

    /**
     * Find all attribute mappings for a specific task definition ID ordered by HTTP location
     */
    List<TaskAttributeMapping> findByTaskDefinitionIdOrderByHttpLocationAsc(Long taskDefinitionId);

    /**
     * Check if a task definition has any attribute mappings configured
     */
    boolean existsByTaskDefinitionId(Long taskDefinitionId);

    /**
     * Count the number of mappings for a task definition
     */
    long countByTaskDefinitionId(Long taskDefinitionId);

    /**
     * Find required mappings for a task definition ID
     */
    @Query("SELECT tam FROM TaskAttributeMapping tam WHERE tam.taskDefinition.id = :taskDefinitionId AND tam.required = true")
    List<TaskAttributeMapping> findRequiredMappingsByTaskDefinitionId(@Param("taskDefinitionId") Long taskDefinitionId);

    /**
     * Find mappings that require transformation for a task definition
     */
    @Query("SELECT tam FROM TaskAttributeMapping tam WHERE tam.taskDefinition.id = :taskDefinitionId AND tam.transformationType != 'NONE'")
    List<TaskAttributeMapping> findMappingsRequiringTransformationByTaskDefinitionId(@Param("taskDefinitionId") Long taskDefinitionId);

    /**
     * Delete all mappings for a task definition ID
     */
    void deleteByTaskDefinitionId(Long taskDefinitionId);

    /**
     * Find mappings by task definition ID and HTTP location
     */
    List<TaskAttributeMapping> findByTaskDefinitionIdAndHttpLocation(Long taskDefinitionId, TaskAttributeMapping.HttpLocation httpLocation);
}
