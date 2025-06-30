-- V2__Add_JSON_Attribute_Extraction.sql
-- Add JSON attribute extraction capabilities to workflow orchestrator

-- Task Attribute Mappings
CREATE TABLE task_attribute_mappings (
    id BIGSERIAL PRIMARY KEY,
    task_definition_id BIGINT NOT NULL,
    task_name VARCHAR(255) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    target_field VARCHAR(255) NOT NULL,
    http_location VARCHAR(50) NOT NULL,
    transformation_type VARCHAR(50) DEFAULT 'NONE',
    transformation_config TEXT,
    required BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_attribute_mapping_task_definition 
        FOREIGN KEY (task_definition_id) REFERENCES task_definitions(id) ON DELETE CASCADE
);

-- Create indexes for performance
CREATE INDEX idx_task_definition_id ON task_attribute_mappings(task_definition_id);
CREATE INDEX idx_task_name ON task_attribute_mappings(task_name);
CREATE INDEX idx_task_name_target_field ON task_attribute_mappings(task_name, target_field);
CREATE INDEX idx_task_attr_mapping_http_location ON task_attribute_mappings(http_location);
CREATE INDEX idx_task_attr_mapping_required ON task_attribute_mappings(required);

-- Create view for task definitions with attribute extraction
CREATE VIEW task_definitions_with_extraction AS
SELECT 
    td.id,
    td.name,
    td.type,
    td.execution_mode,
    COUNT(tam.id) as mapping_count,
    COUNT(CASE WHEN tam.required = true THEN 1 END) as required_mapping_count,
    COUNT(CASE WHEN tam.transformation_type != 'NONE' THEN 1 END) as transformation_count
FROM 
    task_definitions td
    LEFT JOIN task_attribute_mappings tam ON td.id = tam.task_definition_id
GROUP BY 
    td.id, td.name, td.type, td.execution_mode;

-- Create view for attribute mapping summary
CREATE VIEW attribute_mapping_summary AS
SELECT 
    tam.task_name,
    tam.http_location,
    COUNT(*) as mapping_count,
    COUNT(CASE WHEN tam.required = true THEN 1 END) as required_count,
    COUNT(CASE WHEN tam.transformation_type != 'NONE' THEN 1 END) as transformation_count
FROM 
    task_attribute_mappings tam
GROUP BY 
    tam.task_name, tam.http_location;

-- Comments for clarity
COMMENT ON TABLE task_attribute_mappings IS 'Attribute mapping configuration for JSON extraction';
COMMENT ON COLUMN task_attribute_mappings.task_definition_id IS 'Foreign key reference to task_definitions table';
COMMENT ON COLUMN task_attribute_mappings.task_name IS 'Name of the task this mapping applies to (synchronized with task_definition.name)';
COMMENT ON COLUMN task_attribute_mappings.source_path IS 'JsonPointer path to extract from source JSON (e.g., /user/profile/email)';
COMMENT ON COLUMN task_attribute_mappings.target_field IS 'Target field name in the REST request';
COMMENT ON COLUMN task_attribute_mappings.http_location IS 'Where to place the attribute: BODY, QUERY_PARAM, PATH_PARAM, HEADER';
COMMENT ON COLUMN task_attribute_mappings.transformation_type IS 'Type of transformation to apply: NONE, DATE_FORMAT, VALUE_MAP, STRING_FORMAT';
COMMENT ON COLUMN task_attribute_mappings.transformation_config IS 'JSON configuration for the transformation';
COMMENT ON COLUMN task_attribute_mappings.required IS 'Whether this attribute is required for the task execution';

-- Insert sample data for testing (optional - can be removed for production)
-- This shows how attribute mappings would be configured
-- INSERT INTO task_attribute_mappings (task_name, source_path, target_field, http_location, required) VALUES
-- ('user-service', '/customer/personalInfo/email', 'email', 'BODY', true),
-- ('user-service', '/customer/id', 'userId', 'PATH_PARAM', true),
-- ('user-service', '/request/traceId', 'X-Trace-ID', 'HEADER', false),
-- ('order-service', '/order/id', 'orderId', 'QUERY_PARAM', true),
-- ('order-service', '/order/status', 'status', 'BODY', false);
