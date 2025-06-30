package com.example.workfloworchestrator.json.service;

import com.example.workfloworchestrator.json.extraction.AttributeExtractor;
import com.example.workfloworchestrator.json.model.RestApiRequest;
import com.example.workfloworchestrator.json.model.TaskAttributeMapping;
import com.example.workfloworchestrator.json.repository.TaskAttributeMappingRepository;
import com.example.workfloworchestrator.json.transformation.AttributeTransformer;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main service for JSON attribute extraction and request building
 * Orchestrates extraction, transformation, and request construction
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JsonAttributeExtractionService {

    private final TaskAttributeMappingRepository repository;
    private final ObjectMapper objectMapper;
    private final AttributeExtractor attributeExtractor;
    private final Map<String, AttributeTransformer> transformers;

    /**
     * Build REST API request from task definition and source JSON
     * @param taskDefinition the task definition
     * @param sourceJson the source JSON data
     * @return RestApiRequest with extracted and transformed attributes
     */
    @Cacheable(value = "extractedRequests", key = "#taskDefinition.name + '_' + #sourceJson.toString().hashCode()")
    public RestApiRequest buildRequest(TaskDefinition taskDefinition, JsonNode sourceJson) {
        String taskName = taskDefinition.getName();
        log.debug("Building request for task: {} with JSON extraction", taskName);

        List<TaskAttributeMapping> mappings = getMappingsForTask(taskName);
        if (mappings.isEmpty()) {
            log.debug("No attribute mappings found for task: {}", taskName);
            return RestApiRequest.empty();
        }

        return buildRequestFromMappings(mappings, sourceJson);
    }

    /**
     * Get cached attribute mappings for a task
     * @param taskName the task name
     * @return list of attribute mappings
     */
    @Cacheable(value = "attributeMappings", key = "#taskName")
    public List<TaskAttributeMapping> getMappingsForTask(String taskName) {
        return repository.findByTaskName(taskName);
    }

    /**
     * Build REST API request from mappings and source JSON
     * @param mappings the attribute mappings
     * @param sourceJson the source JSON data
     * @return RestApiRequest with extracted attributes
     */
    private RestApiRequest buildRequestFromMappings(List<TaskAttributeMapping> mappings, JsonNode sourceJson) {
        RestApiRequest.RestApiRequestBuilder builder = RestApiRequest.builder();
        
        Map<String, Object> body = new HashMap<>();
        Map<String, String> queryParams = new HashMap<>();
        Map<String, String> pathParams = new HashMap<>();
        Map<String, String> headers = new HashMap<>();

        int extractedCount = 0;

        for (TaskAttributeMapping mapping : mappings) {
            try {
                Object extractedValue = extractAndTransformValue(mapping, sourceJson);
                if (extractedValue != null) {
                    placeValueInRequest(mapping, extractedValue, body, queryParams, pathParams, headers);
                    extractedCount++;
                } else if (mapping.isRequired()) {
                    log.error("Required attribute {} not found for task {}", 
                            mapping.getTargetField(), mapping.getTaskName());
                    throw new IllegalArgumentException(
                            "Required attribute not found: " + mapping.getTargetField());
                }
            } catch (Exception e) {
                if (mapping.isRequired()) {
                    log.error("Failed to extract required attribute {} for task {}: {}", 
                            mapping.getTargetField(), mapping.getTaskName(), e.getMessage());
                    throw new RuntimeException("Failed to extract required attribute: " + 
                            mapping.getTargetField(), e);
                } else {
                    log.warn("Failed to extract optional attribute {} for task {}: {}", 
                            mapping.getTargetField(), mapping.getTaskName(), e.getMessage());
                }
            }
        }

        log.debug("Extracted {} attributes from {} mappings", extractedCount, mappings.size());

        return builder
                .body(!body.isEmpty() ? objectMapper.valueToTree(body) : null)
                .queryParams(queryParams.isEmpty() ? null : queryParams)
                .pathParams(pathParams.isEmpty() ? null : pathParams)
                .headers(headers.isEmpty() ? null : headers)
                .build();
    }

    /**
     * Extract and transform value from source JSON
     * @param mapping the attribute mapping
     * @param sourceJson the source JSON
     * @return extracted and transformed value
     */
    private Object extractAndTransformValue(TaskAttributeMapping mapping, JsonNode sourceJson) {
        try {
            // Extract value using JsonPointer
            Object extractedValue = attributeExtractor.extractValue(sourceJson, mapping.getSourcePath());
            
            if (extractedValue == null) {
                return null;
            }

            // Apply transformation if configured
            if (mapping.hasTransformation()) {
                AttributeTransformer transformer = transformers.get(mapping.getTransformationType().toLowerCase());
                if (transformer != null) {
                    return transformer.transform(extractedValue, mapping.getTransformationConfig());
                } else {
                    log.warn("Transformer not found for type: {}", mapping.getTransformationType());
                }
            }

            return extractedValue;
        } catch (Exception e) {
            log.error("Failed to extract/transform value for mapping {}: {}", mapping.getTargetField(), e.getMessage());
            throw new RuntimeException("Extraction failed for path: " + mapping.getSourcePath(), e);
        }
    }

    /**
     * Place extracted value in appropriate request location
     * @param mapping the attribute mapping
     * @param value the extracted value
     * @param body request body map
     * @param queryParams query parameters map
     * @param pathParams path parameters map
     * @param headers headers map
     */
    private void placeValueInRequest(TaskAttributeMapping mapping, Object value,
                                   Map<String, Object> body,
                                   Map<String, String> queryParams,
                                   Map<String, String> pathParams,
                                   Map<String, String> headers) {
        String targetField = mapping.getTargetField();
        String stringValue = value.toString();

        switch (mapping.getHttpLocation()) {
            case BODY:
                body.put(targetField, value);
                break;
            case QUERY_PARAM:
                queryParams.put(targetField, stringValue);
                break;
            case PATH_PARAM:
                pathParams.put(targetField, stringValue);
                break;
            case HEADER:
                headers.put(targetField, stringValue);
                break;
            default:
                log.warn("Unknown HTTP location: {} for mapping: {}", 
                        mapping.getHttpLocation(), mapping.getTargetField());
        }
    }

    /**
     * Validate that required mappings are present for a task
     * @param taskName the task name
     * @return true if all required mappings are valid
     */
    public boolean validateMappingsForTask(String taskName) {
        List<TaskAttributeMapping> mappings = getMappingsForTask(taskName);
        
        for (TaskAttributeMapping mapping : mappings) {
            if (mapping.getSourcePath() == null || mapping.getSourcePath().trim().isEmpty()) {
                log.error("Invalid source path for mapping: {}", mapping.getTargetField());
                return false;
            }
            if (mapping.getTargetField() == null || mapping.getTargetField().trim().isEmpty()) {
                log.error("Invalid target field for mapping: {}", mapping.getId());
                return false;
            }
            if (mapping.getHttpLocation() == null) {
                log.error("Invalid HTTP location for mapping: {}", mapping.getTargetField());
                return false;
            }
        }
        
        return true;
    }

    /**
     * Get statistics about attribute mappings for a task
     * @param taskName the task name
     * @return mapping statistics
     */
    public Map<String, Object> getMappingStats(String taskName) {
        List<TaskAttributeMapping> mappings = getMappingsForTask(taskName);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMappings", mappings.size());
        stats.put("requiredMappings", mappings.stream().mapToInt(m -> m.isRequired() ? 1 : 0).sum());
        stats.put("transformedMappings", mappings.stream().mapToInt(m -> m.hasTransformation() ? 1 : 0).sum());
        
        Map<String, Long> locationCounts = new HashMap<>();
        mappings.forEach(mapping -> {
            String location = mapping.getHttpLocation().name();
            locationCounts.merge(location, 1L, Long::sum);
        });
        stats.put("locationCounts", locationCounts);
        
        return stats;
    }
}
