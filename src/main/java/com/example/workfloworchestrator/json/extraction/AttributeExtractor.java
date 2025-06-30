package com.example.workfloworchestrator.json.extraction;

import com.example.workfloworchestrator.exception.JsonExtractionException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Interface for extracting attributes from JSON data
 * Provides abstraction for different extraction strategies
 */
public interface AttributeExtractor {

    /**
     * Extract a value from the source JSON using the specified path
     *
     * @param source the source JSON node
     * @param path the path to extract (implementation-specific format)
     * @return the extracted value, or null if not found
     * @throws JsonExtractionException if the path is invalid or extraction fails
     */
    Object extractValue(JsonNode source, String path) throws JsonExtractionException;

    /**
     * Check if a path exists in the source JSON
     *
     * @param source the source JSON node
     * @param path the path to check
     * @return true if the path exists, false otherwise
     */
    boolean pathExists(JsonNode source, String path);

    /**
     * Get the extraction strategy name
     *
     * @return the strategy name
     */
    String getStrategyName();

    /**
     * Validate that a path is syntactically correct
     *
     * @param path the path to validate
     * @throws IllegalArgumentException if the path is invalid
     */
    void validatePath(String path) throws IllegalArgumentException;
}
