-- src/test/resources/schema-h2.sql
-- H2 compatible schema for workflow orchestrator tests

-- Create sequences for primary keys (H2 compatible)
CREATE SEQUENCE IF NOT EXISTS workflow_definitions_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS task_definitions_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS workflow_executions_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS task_executions_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS user_review_points_id_seq START WITH 1 INCREMENT BY 1;

-- Workflow Definitions
CREATE TABLE IF NOT EXISTS workflow_definitions (
                                                    id BIGINT DEFAULT NEXT VALUE FOR workflow_definitions_id_seq PRIMARY KEY,
                                                    name VARCHAR(255) NOT NULL,
    description TEXT,
    version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    strategy_type VARCHAR(50) NOT NULL DEFAULT 'SEQUENTIAL',
    UNIQUE (name, version)
    );

-- Task Definitions
CREATE TABLE IF NOT EXISTS task_definitions (
                                                id BIGINT DEFAULT NEXT VALUE FOR task_definitions_id_seq PRIMARY KEY,
                                                workflow_definition_id BIGINT,
                                                name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(100) NOT NULL,
    execution_order INT NOT NULL,
    retry_limit INT DEFAULT 3,
    timeout_seconds INT DEFAULT 60,
    execution_mode VARCHAR(50) NOT NULL DEFAULT 'API',
    require_user_review BOOLEAN DEFAULT FALSE,
    conditional_expression TEXT,
    next_task_on_success BIGINT,
    next_task_on_failure BIGINT,
    FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definitions(id) ON DELETE CASCADE
    );

-- Task Dependencies
CREATE TABLE IF NOT EXISTS task_dependencies (
                                                 task_definition_id BIGINT NOT NULL,
                                                 dependency_task_id BIGINT NOT NULL,
                                                 PRIMARY KEY (task_definition_id, dependency_task_id),
    FOREIGN KEY (task_definition_id) REFERENCES task_definitions(id) ON DELETE CASCADE
    );

-- Task Definition Configuration
CREATE TABLE IF NOT EXISTS task_definition_config (
                                                      task_definition_id BIGINT NOT NULL,
                                                      config_key VARCHAR(255) NOT NULL,
    config_value TEXT,
    PRIMARY KEY (task_definition_id, config_key),
    FOREIGN KEY (task_definition_id) REFERENCES task_definitions(id) ON DELETE CASCADE
    );

-- Workflow Executions
CREATE TABLE IF NOT EXISTS workflow_executions (
                                                   id BIGINT DEFAULT NEXT VALUE FOR workflow_executions_id_seq PRIMARY KEY,
                                                   workflow_definition_id BIGINT NOT NULL,
                                                   correlation_id VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    current_task_index INT,
    retry_count INT DEFAULT 0,
    error_message TEXT,
    FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definitions(id)
    );

-- Workflow Execution Variables
CREATE TABLE IF NOT EXISTS workflow_execution_variables (
                                                            workflow_execution_id BIGINT NOT NULL,
                                                            variable_key VARCHAR(255) NOT NULL,
    variable_value TEXT,
    PRIMARY KEY (workflow_execution_id, variable_key),
    FOREIGN KEY (workflow_execution_id) REFERENCES workflow_executions(id) ON DELETE CASCADE
    );

-- Task Executions
CREATE TABLE IF NOT EXISTS task_executions (
                                               id BIGINT DEFAULT NEXT VALUE FOR task_executions_id_seq PRIMARY KEY,
                                               workflow_execution_id BIGINT NOT NULL,
                                               task_definition_id BIGINT NOT NULL,
                                               status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    execution_mode VARCHAR(50) NOT NULL,
    retry_count INT DEFAULT 0,
    next_retry_at TIMESTAMP,
    error_message TEXT,
    FOREIGN KEY (workflow_execution_id) REFERENCES workflow_executions(id) ON DELETE CASCADE,
    FOREIGN KEY (task_definition_id) REFERENCES task_definitions(id)
    );

-- Task Execution Inputs
CREATE TABLE IF NOT EXISTS task_execution_inputs (
                                                     task_execution_id BIGINT NOT NULL,
                                                     input_key VARCHAR(255) NOT NULL,
    input_value TEXT,
    PRIMARY KEY (task_execution_id, input_key),
    FOREIGN KEY (task_execution_id) REFERENCES task_executions(id) ON DELETE CASCADE
    );

-- Task Execution Outputs
CREATE TABLE IF NOT EXISTS task_execution_outputs (
                                                      task_execution_id BIGINT NOT NULL,
                                                      output_key VARCHAR(255) NOT NULL,
    output_value TEXT,
    PRIMARY KEY (task_execution_id, output_key),
    FOREIGN KEY (task_execution_id) REFERENCES task_executions(id) ON DELETE CASCADE
    );

-- User Review Points
CREATE TABLE IF NOT EXISTS user_review_points (
                                                  id BIGINT DEFAULT NEXT VALUE FOR user_review_points_id_seq PRIMARY KEY,
                                                  workflow_execution_id BIGINT NOT NULL,
                                                  task_execution_id BIGINT NOT NULL,
                                                  created_at TIMESTAMP NOT NULL,
                                                  reviewed_at TIMESTAMP,
                                                  reviewer VARCHAR(255),
    comment TEXT,
    decision VARCHAR(50),
    FOREIGN KEY (workflow_execution_id) REFERENCES workflow_executions(id) ON DELETE CASCADE,
    FOREIGN KEY (task_execution_id) REFERENCES task_executions(id) ON DELETE CASCADE
    );

-- Create indexes (H2 compatible syntax)
CREATE INDEX IF NOT EXISTS idx_workflow_def_name ON workflow_definitions(name);
CREATE INDEX IF NOT EXISTS idx_task_def_workflow ON task_definitions(workflow_definition_id);
CREATE INDEX IF NOT EXISTS idx_workflow_exec_status ON workflow_executions(status);
CREATE INDEX IF NOT EXISTS idx_task_exec_workflow ON task_executions(workflow_execution_id);
CREATE INDEX IF NOT EXISTS idx_task_exec_status ON task_executions(status);
CREATE INDEX IF NOT EXISTS idx_review_points_workflow ON user_review_points(workflow_execution_id);
