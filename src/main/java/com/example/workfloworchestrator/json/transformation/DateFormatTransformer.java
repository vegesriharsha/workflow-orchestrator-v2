package com.example.workfloworchestrator.json.transformation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Transformer for date/time formatting
 * Supports configuration like: {"inputFormat": "yyyy-MM-dd", "outputFormat": "dd/MM/yyyy"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DateFormatTransformer implements AttributeTransformer {

    private static final String TRANSFORMER_TYPE = "DATE_FORMAT";
    private final ObjectMapper objectMapper;

    @Override
    public Object transform(Object value, String config) throws TransformationException {
        if (value == null) {
            return null;
        }

        if (!canTransform(value)) {
            throw new TransformationException("Cannot transform value of type: " + value.getClass().getSimpleName());
        }

        try {
            DateFormatConfig formatConfig = parseConfig(config);
            String inputValue = value.toString();

            // Parse input date
            Object parsedDate = parseInputDate(inputValue, formatConfig.getInputFormat());

            // Format output date
            return formatOutputDate(parsedDate, formatConfig.getOutputFormat());

        } catch (Exception e) {
            throw new TransformationException("Failed to transform date value: " + value, e);
        }
    }

    @Override
    public String getTransformerType() {
        return TRANSFORMER_TYPE;
    }

    @Override
    public void validateConfiguration(String config) throws IllegalArgumentException {
        if (config == null || config.trim().isEmpty()) {
            throw new IllegalArgumentException("Date format configuration cannot be null or empty");
        }

        try {
            DateFormatConfig formatConfig = parseConfig(config);
            
            // Validate input format
            if (formatConfig.getInputFormat() == null || formatConfig.getInputFormat().trim().isEmpty()) {
                throw new IllegalArgumentException("Input format is required");
            }

            // Validate output format
            if (formatConfig.getOutputFormat() == null || formatConfig.getOutputFormat().trim().isEmpty()) {
                throw new IllegalArgumentException("Output format is required");
            }

            // Test format patterns
            DateTimeFormatter.ofPattern(formatConfig.getInputFormat());
            DateTimeFormatter.ofPattern(formatConfig.getOutputFormat());

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format configuration: " + config, e);
        }
    }

    @Override
    public boolean canTransform(Object value) {
        return value instanceof String || 
               value instanceof LocalDate || 
               value instanceof LocalDateTime || 
               value instanceof ZonedDateTime ||
               value instanceof java.util.Date;
    }

    /**
     * Parse configuration from JSON string
     */
    private DateFormatConfig parseConfig(String config) throws TransformationException {
        try {
            JsonNode configNode = objectMapper.readTree(config);
            
            String inputFormat = configNode.path("inputFormat").asText();
            String outputFormat = configNode.path("outputFormat").asText();

            if (inputFormat.isEmpty() || outputFormat.isEmpty()) {
                throw new TransformationException("Both inputFormat and outputFormat are required");
            }

            return new DateFormatConfig(inputFormat, outputFormat);

        } catch (Exception e) {
            throw new TransformationException("Failed to parse date format configuration: " + config, e);
        }
    }

    /**
     * Parse input date using the specified format
     */
    private Object parseInputDate(String input, String inputFormat) throws DateTimeParseException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(inputFormat);
        
        // Try different date/time types based on the format
        if (inputFormat.contains("HH") || inputFormat.contains("mm") || inputFormat.contains("ss")) {
            if (inputFormat.contains("Z") || inputFormat.contains("X")) {
                return ZonedDateTime.parse(input, formatter);
            } else {
                return LocalDateTime.parse(input, formatter);
            }
        } else {
            return LocalDate.parse(input, formatter);
        }
    }

    /**
     * Format output date using the specified format
     */
    private String formatOutputDate(Object date, String outputFormat) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(outputFormat);
        
        if (date instanceof LocalDate) {
            return ((LocalDate) date).format(formatter);
        } else if (date instanceof LocalDateTime) {
            return ((LocalDateTime) date).format(formatter);
        } else if (date instanceof ZonedDateTime) {
            return ((ZonedDateTime) date).format(formatter);
        } else {
            throw new IllegalArgumentException("Unsupported date type: " + date.getClass());
        }
    }

    /**
     * Configuration class for date formatting
     */
    private static class DateFormatConfig {
        private final String inputFormat;
        private final String outputFormat;

        public DateFormatConfig(String inputFormat, String outputFormat) {
            this.inputFormat = inputFormat;
            this.outputFormat = outputFormat;
        }

        public String getInputFormat() {
            return inputFormat;
        }

        public String getOutputFormat() {
            return outputFormat;
        }
    }
}
