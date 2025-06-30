package com.example.workfloworchestrator.json.extraction;

import com.example.workfloworchestrator.exception.JsonExtractionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JacksonJsonPointerExtractorTest {

    private JacksonJsonPointerExtractor extractor;
    private ObjectMapper objectMapper;
    private JsonNode testData;

    @BeforeEach
    void setUp() throws Exception {
        extractor = new JacksonJsonPointerExtractor();
        objectMapper = new ObjectMapper();
        
        // Create test JSON data
        String testJson = """
            {
                "user": {
                    "id": 123,
                    "profile": {
                        "firstName": "John",
                        "lastName": "Doe",
                        "email": "john.doe@example.com",
                        "age": 30,
                        "active": true
                    }
                },
                "addresses": [
                    {
                        "type": "home",
                        "street": "123 Main St",
                        "city": "New York"
                    },
                    {
                        "type": "work",
                        "street": "456 Business Ave",
                        "city": "Boston"
                    }
                ],
                "metadata": {
                    "timestamp": "2023-01-01T10:00:00Z",
                    "version": "1.0"
                },
                "nullValue": null,
                "emptyString": ""
            }
            """;
        testData = objectMapper.readTree(testJson);
    }

    @Test
    void shouldReturnStrategyName() {
        assertThat(extractor.getStrategyName()).isEqualTo("JsonPointer");
    }

    @Test
    void shouldExtractSimpleStringValue() throws Exception {
        Object result = extractor.extractValue(testData, "/user/profile/firstName");
        
        assertThat(result).isEqualTo("John");
    }

    @Test
    void shouldExtractIntegerValue() throws Exception {
        Object result = extractor.extractValue(testData, "/user/id");
        
        assertThat(result).isEqualTo(123);
    }

    @Test
    void shouldExtractBooleanValue() throws Exception {
        Object result = extractor.extractValue(testData, "/user/profile/active");
        
        assertThat(result).isEqualTo(true);
    }

    @Test
    void shouldExtractNestedValue() throws Exception {
        Object result = extractor.extractValue(testData, "/user/profile/email");
        
        assertThat(result).isEqualTo("john.doe@example.com");
    }

    @Test
    void shouldExtractArrayElement() throws Exception {
        Object result = extractor.extractValue(testData, "/addresses/0/street");
        
        assertThat(result).isEqualTo("123 Main St");
    }

    @Test
    void shouldExtractSecondArrayElement() throws Exception {
        Object result = extractor.extractValue(testData, "/addresses/1/type");
        
        assertThat(result).isEqualTo("work");
    }

    @Test
    void shouldExtractComplexObject() throws Exception {
        Object result = extractor.extractValue(testData, "/user/profile");
        
        assertThat(result).isInstanceOf(JsonNode.class);
        JsonNode profileNode = (JsonNode) result;
        assertThat(profileNode.get("firstName").asText()).isEqualTo("John");
        assertThat(profileNode.get("email").asText()).isEqualTo("john.doe@example.com");
    }

    @Test
    void shouldReturnNullForMissingPath() throws Exception {
        Object result = extractor.extractValue(testData, "/user/profile/nonexistent");
        
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullForInvalidArrayIndex() throws Exception {
        Object result = extractor.extractValue(testData, "/addresses/5/street");
        
        assertThat(result).isNull();
    }

    @Test
    void shouldHandleNullValue() throws Exception {
        Object result = extractor.extractValue(testData, "/nullValue");
        
        assertThat(result).isNull();
    }

    @Test
    void shouldHandleEmptyString() throws Exception {
        Object result = extractor.extractValue(testData, "/emptyString");
        
        assertThat(result).isEqualTo("");
    }

    @Test
    void shouldNormalizePathWithoutLeadingSlash() throws Exception {
        Object result = extractor.extractValue(testData, "user/profile/firstName");
        
        assertThat(result).isEqualTo("John");
    }

    @Test
    void shouldCheckPathExists() {
        assertThat(extractor.pathExists(testData, "/user/profile/email")).isTrue();
        assertThat(extractor.pathExists(testData, "/addresses/0/street")).isTrue();
        assertThat(extractor.pathExists(testData, "/user/profile/nonexistent")).isFalse();
        assertThat(extractor.pathExists(testData, "/addresses/5/street")).isFalse();
    }

    @Test
    void shouldValidateValidPaths() {
        assertThatNoException().isThrownBy(() -> {
            extractor.validatePath("/user/profile/email");
            extractor.validatePath("/addresses/0/street");
            extractor.validatePath("user/id");
        });
    }

    @Test
    void shouldThrowExceptionForNullSource() {
        assertThatThrownBy(() -> extractor.extractValue(null, "/user/id"))
                .isInstanceOf(JsonExtractionException.class)
                .hasMessage("Source JSON is null");
    }

    @Test
    void shouldThrowExceptionForNullPath() {
        assertThatThrownBy(() -> extractor.extractValue(testData, null))
                .isInstanceOf(JsonExtractionException.class)
                .hasMessage("Path cannot be null or empty");
    }

    @Test
    void shouldThrowExceptionForEmptyPath() {
        assertThatThrownBy(() -> extractor.extractValue(testData, ""))
                .isInstanceOf(JsonExtractionException.class)
                .hasMessage("Path cannot be null or empty");
    }

    @Test
    void shouldThrowExceptionForInvalidPathValidation() {
        assertThatThrownBy(() -> extractor.validatePath(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Path cannot be null or empty");
                
        assertThatThrownBy(() -> extractor.validatePath(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Path cannot be null or empty");
    }

    @Test
    void shouldReturnFalseForPathExistsWithNullSource() {
        assertThat(extractor.pathExists(null, "/user/id")).isFalse();
    }

    @Test
    void shouldReturnFalseForPathExistsWithNullPath() {
        assertThat(extractor.pathExists(testData, null)).isFalse();
        assertThat(extractor.pathExists(testData, "")).isFalse();
    }

    @Test
    void shouldExtractRootValue() throws Exception {
        JsonNode rootUser = testData.get("user");
        Object result = extractor.extractValue(rootUser, "/id");
        
        assertThat(result).isEqualTo(123);
    }

    @Test
    void shouldHandleSpecialCharactersInPath() throws Exception {
        // Create test data with special characters
        String specialJson = """
            {
                "field-with-dash": "value1",
                "field_with_underscore": "value2",
                "field.with.dots": "value3"
            }
            """;
        JsonNode specialData = objectMapper.readTree(specialJson);
        
        Object result1 = extractor.extractValue(specialData, "/field-with-dash");
        Object result2 = extractor.extractValue(specialData, "/field_with_underscore");
        Object result3 = extractor.extractValue(specialData, "/field.with.dots");
        
        assertThat(result1).isEqualTo("value1");
        assertThat(result2).isEqualTo("value2");
        assertThat(result3).isEqualTo("value3");
    }
}
