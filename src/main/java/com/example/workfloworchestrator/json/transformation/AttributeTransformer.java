package com.example.workfloworchestrator.json.transformation;

/**
 * Interface for transforming extracted attribute values
 * Allows for data conversion, formatting, and mapping before use in REST requests
 */
public interface AttributeTransformer {

    /**
     * Transform a value using the provided configuration
     *
     * @param value the value to transform
     * @param config the transformation configuration (JSON string or simple config)
     * @return the transformed value
     * @throws TransformationException if transformation fails
     */
    Object transform(Object value, String config) throws TransformationException;

    /**
     * Get the transformer type
     *
     * @return the transformer type
     */
    String getTransformerType();

    /**
     * Validate the transformation configuration
     *
     * @param config the configuration to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    void validateConfiguration(String config) throws IllegalArgumentException;

    /**
     * Check if this transformer can handle the given value type
     *
     * @param value the value to check
     * @return true if the transformer can handle this value type
     */
    boolean canTransform(Object value);

    /**
     * Exception thrown when transformation fails
     */
    class TransformationException extends Exception {
        public TransformationException(String message) {
            super(message);
        }

        public TransformationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
