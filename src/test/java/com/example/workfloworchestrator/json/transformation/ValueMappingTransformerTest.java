package com.example.workfloworchestrator.json.transformation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ValueMappingTransformerTest {

    private ValueMappingTransformer transformer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        transformer = new ValueMappingTransformer(objectMapper);
    }

    @Test
    void shouldReturnTransformerType() {
        assertThat(transformer.getTransformerType()).isEqualTo("VALUE_MAP");
    }

    @Test
    void shouldTransformMappedValue() throws Exception {
        String config = """
            {
                "mappings": {
                    "ACTIVE": "A",
                    "INACTIVE": "I",
                    "PENDING": "P"
                }
            }
            """;

        Object result = transformer.transform("ACTIVE", config);

        assertThat(result).isEqualTo("A");
    }

    @Test
    void shouldTransformWithDefaultValue() throws Exception {
        String config = """
            {
                "mappings": {
                    "ACTIVE": "A",
                    "INACTIVE": "I"
                },
                "defaultValue": "UNKNOWN"
            }
            """;

        Object result = transformer.transform("PENDING", config);

        assertThat(result).isEqualTo("UNKNOWN");
    }

    @Test
    void shouldReturnOriginalValueWhenNoMappingAndNoDefault() throws Exception {
        String config = """
            {
                "mappings": {
                    "ACTIVE": "A",
                    "INACTIVE": "I"
                }
            }
            """;

        Object result = transformer.transform("PENDING", config);

        assertThat(result).isEqualTo("PENDING");
    }

    @Test
    void shouldTransformNumericValues() throws Exception {
        String config = """
            {
                "mappings": {
                    "1": "ONE",
                    "2": "TWO",
                    "3": "THREE"
                }
            }
            """;

        Object result = transformer.transform(1, config);

        assertThat(result).isEqualTo("ONE");
    }

    @Test
    void shouldTransformBooleanValues() throws Exception {
        String config = """
            {
                "mappings": {
                    "true": "YES",
                    "false": "NO"
                }
            }
            """;

        Object result = transformer.transform(true, config);

        assertThat(result).isEqualTo("YES");
    }

    @Test
    void shouldHandleNullValueWithMapping() throws Exception {
        String config = """
            {
                "mappings": {
                    "null": "NULL_VALUE",
                    "ACTIVE": "A"
                }
            }
            """;

        Object result = transformer.transform(null, config);

        assertThat(result).isEqualTo("NULL_VALUE");
    }

    @Test
    void shouldHandleNullValueWithDefault() throws Exception {
        String config = """
            {
                "mappings": {
                    "ACTIVE": "A"
                },
                "defaultValue": "DEFAULT_NULL"
            }
            """;

        Object result = transformer.transform(null, config);

        assertThat(result).isEqualTo("DEFAULT_NULL");
    }

    @Test
    void shouldReturnNullForNullValueWithoutMappingOrDefault() throws Exception {
        String config = """
            {
                "mappings": {
                    "ACTIVE": "A"
                }
            }
            """;

        Object result = transformer.transform(null, config);

        assertThat(result).isNull();
    }

    @Test
    void shouldThrowExceptionInStrictMode() {
        String config = """
            {
                "mappings": {
                    "ACTIVE": "A",
                    "INACTIVE": "I"
                },
                "strictMode": true
            }
            """;

        assertThatThrownBy(() -> transformer.transform("PENDING", config))
                .isInstanceOf(AttributeTransformer.TransformationException.class)
                .hasMessageContaining("No mapping found for value: PENDING and strict mode is enabled");
    }

    @Test
    void shouldUseDefaultValueInStrictModeWhenConfigured() throws Exception {
        String config = """
            {
                "mappings": {
                    "ACTIVE": "A",
                    "INACTIVE": "I"
                },
                "defaultValue": "UNKNOWN",
                "strictMode": true
            }
            """;

        Object result = transformer.transform("PENDING", config);

        assertThat(result).isEqualTo("UNKNOWN");
    }

    @Test
    void shouldValidateValidConfiguration() {
        String config = """
            {
                "mappings": {
                    "ACTIVE": "A",
                    "INACTIVE": "I"
                }
            }
            """;

        assertThatNoException().isThrownBy(() -> transformer.validateConfiguration(config));
    }

    @Test
    void shouldValidateConfigurationWithDefaultValue() {
        String config = """
            {
                "defaultValue": "DEFAULT"
            }
            """;

        assertThatNoException().isThrownBy(() -> transformer.validateConfiguration(config));
    }

    @Test
    void shouldThrowExceptionForNullConfiguration() {
        assertThatThrownBy(() -> transformer.validateConfiguration(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Value mapping configuration cannot be null or empty");
    }

    @Test
    void shouldThrowExceptionForEmptyConfiguration() {
        assertThatThrownBy(() -> transformer.validateConfiguration(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Value mapping configuration cannot be null or empty");
    }

    @Test
    void shouldThrowExceptionForEmptyMappingsAndNoDefault() {
        String config = """
            {
                "mappings": {}
            }
            """;

        assertThatThrownBy(() -> transformer.validateConfiguration(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid value mapping configuration");
    }

    @Test
    void shouldCheckCanTransformForAllTypes() {
        assertThat(transformer.canTransform("string")).isTrue();
        assertThat(transformer.canTransform(123)).isTrue();
        assertThat(transformer.canTransform(true)).isTrue();
        assertThat(transformer.canTransform(null)).isTrue();
        assertThat(transformer.canTransform(new Object())).isTrue();
    }

    @Test
    void shouldHandleCaseSensitiveMapping() throws Exception {
        String config = """
            {
                "mappings": {
                    "Active": "A",
                    "ACTIVE": "AA"
                }
            }
            """;

        Object result1 = transformer.transform("Active", config);
        Object result2 = transformer.transform("ACTIVE", config);

        assertThat(result1).isEqualTo("A");
        assertThat(result2).isEqualTo("AA");
    }

    @Test
    void shouldHandleComplexMappings() throws Exception {
        String config = """
            {
                "mappings": {
                    "HIGH": "Critical Priority",
                    "MEDIUM": "Normal Priority",
                    "LOW": "Low Priority"
                },
                "defaultValue": "Unspecified Priority"
            }
            """;

        Object result1 = transformer.transform("HIGH", config);
        Object result2 = transformer.transform("UNKNOWN", config);

        assertThat(result1).isEqualTo("Critical Priority");
        assertThat(result2).isEqualTo("Unspecified Priority");
    }

    @Test
    void shouldHandleEmptyStringMapping() throws Exception {
        String config = """
            {
                "mappings": {
                    "": "EMPTY_STRING",
                    " ": "SPACE"
                }
            }
            """;

        Object result1 = transformer.transform("", config);
        Object result2 = transformer.transform(" ", config);

        assertThat(result1).isEqualTo("EMPTY_STRING");
        assertThat(result2).isEqualTo("SPACE");
    }

    @Test
    void shouldPreserveMappingOrder() throws Exception {
        String config = """
            {
                "mappings": {
                    "FIRST": "1st",
                    "SECOND": "2nd",
                    "THIRD": "3rd"
                }
            }
            """;

        Object result1 = transformer.transform("FIRST", config);
        Object result2 = transformer.transform("SECOND", config);
        Object result3 = transformer.transform("THIRD", config);

        assertThat(result1).isEqualTo("1st");
        assertThat(result2).isEqualTo("2nd");
        assertThat(result3).isEqualTo("3rd");
    }
}
