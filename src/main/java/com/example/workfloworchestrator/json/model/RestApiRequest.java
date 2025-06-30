package com.example.workfloworchestrator.json.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a complete REST API request with all components
 * Built from extracted JSON attributes for microservice calls
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestApiRequest {

    @Builder.Default
    private JsonNode body = null;

    @Builder.Default
    private Map<String, String> queryParams = new HashMap<>();

    @Builder.Default
    private Map<String, String> pathParams = new HashMap<>();

    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    /**
     * Create an empty REST API request
     * @return empty RestApiRequest
     */
    public static RestApiRequest empty() {
        return RestApiRequest.builder()
                .body(null)
                .queryParams(new HashMap<>())
                .pathParams(new HashMap<>())
                .headers(new HashMap<>())
                .build();
    }

    /**
     * Check if the request has a body
     */
    public boolean hasBody() {
        return body != null && !body.isNull() && !body.isEmpty();
    }

    /**
     * Check if the request has query parameters
     */
    public boolean hasQueryParams() {
        return !queryParams.isEmpty();
    }

    /**
     * Check if the request has path parameters
     */
    public boolean hasPathParams() {
        return !pathParams.isEmpty();
    }

    /**
     * Check if the request has headers
     */
    public boolean hasHeaders() {
        return !headers.isEmpty();
    }

    /**
     * Add a query parameter
     */
    public void addQueryParam(String key, String value) {
        queryParams.put(key, value);
    }

    /**
     * Add a path parameter
     */
    public void addPathParam(String key, String value) {
        pathParams.put(key, value);
    }

    /**
     * Add a header
     */
    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    /**
     * Get the total number of extracted attributes
     */
    public int getTotalAttributeCount() {
        int count = queryParams.size() + pathParams.size() + headers.size();
        if (hasBody()) {
            count += countBodyFields(body);
        }
        return count;
    }

    private int countBodyFields(JsonNode node) {
        if (node.isObject()) {
            return node.size();
        } else if (node.isArray()) {
            return node.size();
        } else {
            return 1;
        }
    }
}
