package com.example.workfloworchestrator.json.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RestApiRequestTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldCreateEmptyRequest() {
        RestApiRequest request = RestApiRequest.builder().build();

        assertThat(request.hasBody()).isFalse();
        assertThat(request.hasQueryParams()).isFalse();
        assertThat(request.hasPathParams()).isFalse();
        assertThat(request.hasHeaders()).isFalse();
        assertThat(request.getTotalAttributeCount()).isEqualTo(0);
    }

    @Test
    void shouldCreateRequestWithBody() throws Exception {
        JsonNode body = objectMapper.readTree("{\"name\":\"John\",\"age\":30}");
        RestApiRequest request = RestApiRequest.builder()
                .body(body)
                .build();

        assertThat(request.hasBody()).isTrue();
        assertThat(request.getBody().get("name").asText()).isEqualTo("John");
        assertThat(request.getBody().get("age").asInt()).isEqualTo(30);
        assertThat(request.getTotalAttributeCount()).isEqualTo(2);
    }

    @Test
    void shouldCreateRequestWithQueryParams() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("page", "1");
        queryParams.put("size", "10");

        RestApiRequest request = RestApiRequest.builder()
                .queryParams(queryParams)
                .build();

        assertThat(request.hasQueryParams()).isTrue();
        assertThat(request.getQueryParams()).hasSize(2);
        assertThat(request.getQueryParams().get("page")).isEqualTo("1");
        assertThat(request.getQueryParams().get("size")).isEqualTo("10");
        assertThat(request.getTotalAttributeCount()).isEqualTo(2);
    }

    @Test
    void shouldCreateRequestWithPathParams() {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("id", "123");
        pathParams.put("version", "v1");

        RestApiRequest request = RestApiRequest.builder()
                .pathParams(pathParams)
                .build();

        assertThat(request.hasPathParams()).isTrue();
        assertThat(request.getPathParams()).hasSize(2);
        assertThat(request.getPathParams().get("id")).isEqualTo("123");
        assertThat(request.getPathParams().get("version")).isEqualTo("v1");
        assertThat(request.getTotalAttributeCount()).isEqualTo(2);
    }

    @Test
    void shouldCreateRequestWithHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token");
        headers.put("Content-Type", "application/json");

        RestApiRequest request = RestApiRequest.builder()
                .headers(headers)
                .build();

        assertThat(request.hasHeaders()).isTrue();
        assertThat(request.getHeaders()).hasSize(2);
        assertThat(request.getHeaders().get("Authorization")).isEqualTo("Bearer token");
        assertThat(request.getHeaders().get("Content-Type")).isEqualTo("application/json");
        assertThat(request.getTotalAttributeCount()).isEqualTo(2);
    }

    @Test
    void shouldCreateComplexRequest() throws Exception {
        JsonNode body = objectMapper.readTree("{\"user\":{\"name\":\"John\"}}");
        Map<String, String> queryParams = Map.of("filter", "active");
        Map<String, String> pathParams = Map.of("id", "123");
        Map<String, String> headers = Map.of("X-Trace-ID", "trace-123");

        RestApiRequest request = RestApiRequest.builder()
                .body(body)
                .queryParams(queryParams)
                .pathParams(pathParams)
                .headers(headers)
                .build();

        assertThat(request.hasBody()).isTrue();
        assertThat(request.hasQueryParams()).isTrue();
        assertThat(request.hasPathParams()).isTrue();
        assertThat(request.hasHeaders()).isTrue();
        assertThat(request.getTotalAttributeCount()).isEqualTo(4);
    }

    @Test
    void shouldAddQueryParam() {
        RestApiRequest request = RestApiRequest.builder().build();
        request.addQueryParam("key", "value");

        assertThat(request.hasQueryParams()).isTrue();
        assertThat(request.getQueryParams().get("key")).isEqualTo("value");
    }

    @Test
    void shouldAddPathParam() {
        RestApiRequest request = RestApiRequest.builder().build();
        request.addPathParam("id", "123");

        assertThat(request.hasPathParams()).isTrue();
        assertThat(request.getPathParams().get("id")).isEqualTo("123");
    }

    @Test
    void shouldAddHeader() {
        RestApiRequest request = RestApiRequest.builder().build();
        request.addHeader("Authorization", "Bearer token");

        assertThat(request.hasHeaders()).isTrue();
        assertThat(request.getHeaders().get("Authorization")).isEqualTo("Bearer token");
    }

    @Test
    void shouldHandleNullBody() {
        RestApiRequest request = RestApiRequest.builder()
                .body(null)
                .build();

        assertThat(request.hasBody()).isFalse();
        assertThat(request.getBody()).isNull();
    }

    @Test
    void shouldHandleEmptyMaps() {
        RestApiRequest request = RestApiRequest.builder()
                .queryParams(new HashMap<>())
                .pathParams(new HashMap<>())
                .headers(new HashMap<>())
                .build();

        assertThat(request.hasQueryParams()).isFalse();
        assertThat(request.hasPathParams()).isFalse();
        assertThat(request.hasHeaders()).isFalse();
        assertThat(request.getTotalAttributeCount()).isEqualTo(0);
    }

    @Test
    void shouldCountArrayBodyFields() throws Exception {
        JsonNode arrayBody = objectMapper.readTree("[{\"name\":\"John\"},{\"name\":\"Jane\"}]");
        RestApiRequest request = RestApiRequest.builder()
                .body(arrayBody)
                .build();

        assertThat(request.hasBody()).isTrue();
        assertThat(request.getTotalAttributeCount()).isEqualTo(2);
    }

    @Test
    void shouldCountSingleValueBody() throws Exception {
        JsonNode valueBody = objectMapper.readTree("\"simple string\"");
        RestApiRequest request = RestApiRequest.builder()
                .body(valueBody)
                .build();

        assertThat(request.hasBody()).isTrue();
        assertThat(request.getTotalAttributeCount()).isEqualTo(1);
    }
}
