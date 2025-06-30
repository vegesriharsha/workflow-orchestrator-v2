package com.example.workfloworchestrator.json.extraction;

import com.example.workfloworchestrator.exception.JsonExtractionException;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JSON attribute extractor using Jackson JsonPointer (RFC 6901)
 * Supports paths like "/user/profile/email" or "/addresses/0/street"
 */
@Slf4j
@Component
public class JacksonJsonPointerExtractor implements AttributeExtractor {

    private static final String STRATEGY_NAME = "JsonPointer";

    @Override
    public Object extractValue(JsonNode source, String path) throws JsonExtractionException {
        if (source == null) {
            throw new JsonExtractionException("Source JSON is null");
        }

        if (path == null || path.isEmpty()) {
            throw new JsonExtractionException("Path cannot be null or empty");
        }

        try {
            // Ensure path starts with '/' for JsonPointer
            String normalizedPath = normalizePath(path);
            JsonPointer pointer = JsonPointer.compile(normalizedPath);
            JsonNode result = source.at(pointer);

            if (result.isMissingNode()) {
                log.debug("Path not found: {} in source JSON", normalizedPath);
                return null;
            }

            // Convert JsonNode to appropriate Java type
            return convertJsonNodeToValue(result);

        } catch (Exception e) {
            throw new JsonExtractionException("Failed to extract value at path: " + path, e);
        }
    }

    @Override
    public boolean pathExists(JsonNode source, String path) {
        if (source == null || path == null || path.isEmpty()) {
            return false;
        }

        try {
            String normalizedPath = normalizePath(path);
            JsonPointer pointer = JsonPointer.compile(normalizedPath);
            JsonNode result = source.at(pointer);
            return !result.isMissingNode();
        } catch (Exception e) {
            log.debug("Error checking path existence: {}", path, e);
            return false;
        }
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public void validatePath(String path) throws IllegalArgumentException {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        try {
            String normalizedPath = normalizePath(path);
            JsonPointer.compile(normalizedPath);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JsonPointer path: " + path, e);
        }
    }

    /**
     * Normalize path to ensure it starts with '/' for JsonPointer
     */
    private String normalizePath(String path) {
        if (path.startsWith("/")) {
            return path;
        }
        return "/" + path;
    }

    /**
     * Convert JsonNode to appropriate Java value
     */
    private Object convertJsonNodeToValue(JsonNode node) {
        if (node.isNull()) {
            return null;
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isInt()) {
            return node.asInt();
        } else if (node.isLong()) {
            return node.asLong();
        } else if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        } else if (node.isTextual()) {
            return node.asText();
        } else if (node.isArray() || node.isObject()) {
            // Return complex objects as JsonNode for further processing
            return node;
        } else {
            // Fallback to text representation
            return node.asText();
        }
    }
}
