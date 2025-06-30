package com.example.workfloworchestrator.json.transformation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Transformer for value mapping/translation
 * Supports configuration like: {"mappings": {"ACTIVE": "A", "INACTIVE": "I"}, "defaultValue": "U"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValueMappingTransformer implements AttributeTransformer {

    private static final String TRANSFORMER_TYPE = "VALUE_MAP";
    private final ObjectMapper objectMapper;

    @Override
    public Object transform(Object value, String config) throws TransformationException {
        if (value == null) {
            return handleNullValue(config);
        }

        try {
            ValueMappingConfig mappingConfig = parseConfig(config);
            String inputValue = value.toString();

            // Look up the mapped value
            String mappedValue = mappingConfig.getMappings().get(inputValue);
            
            if (mappedValue != null) {
                return mappedValue;
            }

            // Return default value if configured, otherwise return original value
            if (mappingConfig.getDefaultValue() != null) {
                return mappingConfig.getDefaultValue();
            }

            // If strict mode is enabled and no mapping found, throw exception
            if (mappingConfig.isStrictMode()) {
                throw new TransformationException("No mapping found for value: " + inputValue + " and strict mode is enabled");
            }

            return inputValue; // Return original value

        } catch (TransformationException e) {
            throw e;
        } catch (Exception e) {
            throw new TransformationException("Failed to transform value: " + value, e);
        }
    }

    @Override
    public String getTransformerType() {
        return TRANSFORMER_TYPE;
    }

    @Override
    public void validateConfiguration(String config) throws IllegalArgumentException {
        if (config == null || config.trim().isEmpty()) {
            throw new IllegalArgumentException("Value mapping configuration cannot be null or empty");
        }

        try {
            parseConfig(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid value mapping configuration: " + config, e);
        }
    }

    @Override
    public boolean canTransform(Object value) {
        // Can transform any value that can be converted to string
        return true;
    }

    /**
     * Handle null values based on configuration
     */
    private Object handleNullValue(String config) throws TransformationException {
        try {
            ValueMappingConfig mappingConfig = parseConfig(config);
            
            // Check if there's a specific mapping for null values
            String nullMapping = mappingConfig.getMappings().get("null");
            if (nullMapping != null) {
                return nullMapping;
            }

            // Return default value if configured
            if (mappingConfig.getDefaultValue() != null) {
                return mappingConfig.getDefaultValue();
            }

            return null;

        } catch (Exception e) {
            throw new TransformationException("Failed to handle null value", e);
        }
    }

    /**
     * Parse configuration from JSON string
     */
    private ValueMappingConfig parseConfig(String config) throws TransformationException {
        try {
            JsonNode configNode = objectMapper.readTree(config);
            
            Map<String, String> mappings = new HashMap<>();
            String defaultValue = null;
            boolean strictMode = false;

            // Parse mappings
            JsonNode mappingsNode = configNode.path("mappings");
            if (mappingsNode.isObject()) {
                mappingsNode.fields().forEachRemaining(entry -> 
                    mappings.put(entry.getKey(), entry.getValue().asText()));
            }

            // Parse default value
            JsonNode defaultNode = configNode.path("defaultValue");
            if (!defaultNode.isMissingNode() && !defaultNode.isNull()) {
                defaultValue = defaultNode.asText();
            }

            // Parse strict mode
            JsonNode strictNode = configNode.path("strictMode");
            if (!strictNode.isMissingNode()) {
                strictMode = strictNode.asBoolean();
            }

            if (mappings.isEmpty() && defaultValue == null) {
                throw new TransformationException("Either mappings or defaultValue must be provided");
            }

            return new ValueMappingConfig(mappings, defaultValue, strictMode);

        } catch (Exception e) {
            throw new TransformationException("Failed to parse value mapping configuration: " + config, e);
        }
    }

    /**
     * Configuration class for value mapping
     */
    private static class ValueMappingConfig {
        private final Map<String, String> mappings;
        private final String defaultValue;
        private final boolean strictMode;

        public ValueMappingConfig(Map<String, String> mappings, String defaultValue, boolean strictMode) {
            this.mappings = mappings;
            this.defaultValue = defaultValue;
            this.strictMode = strictMode;
        }

        public Map<String, String> getMappings() {
            return mappings;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public boolean isStrictMode() {
            return strictMode;
        }
    }
}
