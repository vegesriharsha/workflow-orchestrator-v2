-- V1__Initial_Schema.sql
-- Complete database schema for Workflow Orchestrator with all entities

-- Workflow Definitions
CREATE TABLE workflow_definitions (
                                      id BIGSERIAL PRIMARY KEY,
                                      name VARCHAR(255) NOT NULL,
                                      description TEXT,
                                      version VARCHAR(50) NOT NULL,
                                      created_at TIMESTAMP NOT NULL,
                                      updated_at TIMESTAMP,
                                      strategy_type VARCHAR(50) NOT NULL DEFAULT 'SEQUENTIAL',
                                      UNIQUE (name, version)
);

-- Task Definitions
CREATE TABLE task_definitions (
                                  id BIGSERIAL PRIMARY KEY,
                                  workflow_definition_id BIGINT NOT NULL,
                                  name VARCHAR(255) NOT NULL,
                                  description TEXT,
                                  type VARCHAR(100) NOT NULL,
                                  execution_order INT,
                                  retry_limit INT,
                                  timeout_seconds INT,
                                  execution_mode VARCHAR(50) DEFAULT 'API',
                                  require_user_review BOOLEAN DEFAULT FALSE,
                                  conditional_expression TEXT,
                                  next_task_on_success BIGINT,
                                  next_task_on_failure BIGINT,
                                  CONSTRAINT fk_task_def_workflow
                                      FOREIGN KEY (workflow_definition_id)
                                          REFERENCES workflow_definitions(id)
                                          ON DELETE CASCADE
);

-- Task Dependencies
CREATE TABLE task_dependencies (
                                   task_definition_id BIGINT NOT NULL,
                                   dependency_task_id BIGINT NOT NULL,
                                   PRIMARY KEY (task_definition_id, dependency_task_id),
                                   CONSTRAINT fk_task_dependencies_task
                                       FOREIGN KEY (task_definition_id)
                                           REFERENCES task_definitions(id)
                                           ON DELETE CASCADE
);

-- Task Definition Configuration
CREATE TABLE task_definition_config (
                                        task_definition_id BIGINT NOT NULL,
                                        config_key VARCHAR(255) NOT NULL,
                                        config_value TEXT,
                                        PRIMARY KEY (task_definition_id, config_key),
                                        CONSTRAINT fk_task_config_task
                                            FOREIGN KEY (task_definition_id)
                                                REFERENCES task_definitions(id)
                                                ON DELETE CASCADE
);

-- Workflow Executions
CREATE TABLE workflow_executions (
                                     id BIGSERIAL PRIMARY KEY,
                                     workflow_definition_id BIGINT NOT NULL,
                                     correlation_id VARCHAR(255) NOT NULL UNIQUE,
                                     status VARCHAR(50) NOT NULL,
                                     started_at TIMESTAMP,
                                     completed_at TIMESTAMP,
                                     current_task_index INT,
                                     retry_count INT DEFAULT 0,
                                     error_message TEXT,
                                     CONSTRAINT fk_workflow_exec_definition
                                         FOREIGN KEY (workflow_definition_id)
                                             REFERENCES workflow_definitions(id)
);

-- Workflow Execution Variables
CREATE TABLE workflow_execution_variables (
                                              workflow_execution_id BIGINT NOT NULL,
                                              variable_key VARCHAR(255) NOT NULL,
                                              variable_value TEXT,
                                              PRIMARY KEY (workflow_execution_id, variable_key),
                                              CONSTRAINT fk_workflow_var_execution
                                                  FOREIGN KEY (workflow_execution_id)
                                                      REFERENCES workflow_executions(id)
                                                      ON DELETE CASCADE
);

-- Task Executions
CREATE TABLE task_executions (
                                 id BIGSERIAL PRIMARY KEY,
                                 workflow_execution_id BIGINT NOT NULL,
                                 task_definition_id BIGINT NOT NULL,
                                 status VARCHAR(50) NOT NULL,
                                 started_at TIMESTAMP,
                                 completed_at TIMESTAMP,
                                 execution_mode VARCHAR(50),
                                 retry_count INT DEFAULT 0,
                                 next_retry_at TIMESTAMP,
                                 error_message TEXT,
                                 CONSTRAINT fk_task_exec_workflow
                                     FOREIGN KEY (workflow_execution_id)
                                         REFERENCES workflow_executions(id)
                                         ON DELETE CASCADE,
                                 CONSTRAINT fk_task_exec_definition
                                     FOREIGN KEY (task_definition_id)
                                         REFERENCES task_definitions(id)
);

-- Task Execution Inputs
CREATE TABLE task_execution_inputs (
                                       task_execution_id BIGINT NOT NULL,
                                       input_key VARCHAR(255) NOT NULL,
                                       input_value TEXT,
                                       PRIMARY KEY (task_execution_id, input_key),
                                       CONSTRAINT fk_task_input_execution
                                           FOREIGN KEY (task_execution_id)
                                               REFERENCES task_executions(id)
                                               ON DELETE CASCADE
);

-- Task Execution Outputs
CREATE TABLE task_execution_outputs (
                                        task_execution_id BIGINT NOT NULL,
                                        output_key VARCHAR(255) NOT NULL,
                                        output_value TEXT,
                                        PRIMARY KEY (task_execution_id, output_key),
                                        CONSTRAINT fk_task_output_execution
                                            FOREIGN KEY (task_execution_id)
                                                REFERENCES task_executions(id)
                                                ON DELETE CASCADE
);

-- User Review Points
CREATE TABLE user_review_points (
                                    id BIGSERIAL PRIMARY KEY,
                                    task_execution_id BIGINT NOT NULL,
                                    workflow_execution_id BIGINT NOT NULL,
                                    created_at TIMESTAMP NOT NULL,
                                    reviewed_at TIMESTAMP,
                                    reviewer VARCHAR(255),
                                    comment TEXT,
                                    decision VARCHAR(50),
                                    CONSTRAINT fk_review_task_execution
                                        FOREIGN KEY (task_execution_id)
                                            REFERENCES task_executions(id)
                                            ON DELETE CASCADE,
                                    CONSTRAINT fk_review_workflow_execution
                                        FOREIGN KEY (workflow_execution_id)
                                            REFERENCES workflow_executions(id)
                                            ON DELETE CASCADE
);

-- Create views for monitoring
CREATE VIEW active_workflows AS
SELECT
    we.id,
    wd.name as workflow_name,
    wd.version,
    we.correlation_id,
    we.status,
    we.started_at,
    now() - we.started_at as duration,
    we.current_task_index,
    (SELECT count(*) FROM task_executions te WHERE te.workflow_execution_id = we.id) as total_tasks,
    (SELECT count(*) FROM task_executions te WHERE te.workflow_execution_id = we.id AND te.status = 'COMPLETED') as completed_tasks
FROM
    workflow_executions we
        JOIN workflow_definitions wd ON we.workflow_definition_id = wd.id
WHERE
    we.status IN ('CREATED', 'RUNNING', 'PAUSED', 'AWAITING_USER_REVIEW');

-- Create view for pending user reviews
CREATE VIEW pending_user_reviews AS
SELECT
    urp.id as review_id,
    we.id as workflow_execution_id,
    wd.name as workflow_name,
    wd.version as workflow_version,
    we.correlation_id,
    te.id as task_execution_id,
    td.name as task_name,
    td.type as task_type,
    urp.created_at as review_requested_at,
    now() - urp.created_at as pending_duration
FROM
    user_review_points urp
        JOIN workflow_executions we ON urp.workflow_execution_id = we.id
        JOIN workflow_definitions wd ON we.workflow_definition_id = wd.id
        JOIN task_executions te ON urp.task_execution_id = te.id
        JOIN task_definitions td ON te.task_definition_id = td.id
WHERE
    urp.reviewed_at IS NULL
ORDER BY
    urp.created_at ASC;

-- Create view for workflow execution summary
CREATE VIEW workflow_execution_summary AS
SELECT
    we.id,
    wd.name as workflow_name,
    wd.version,
    we.correlation_id,
    we.status,
    we.started_at,
    we.completed_at,
    we.error_message,
    we.retry_count,
    (SELECT count(*) FROM task_executions WHERE workflow_execution_id = we.id) as total_tasks,
    (SELECT count(*) FROM task_executions WHERE workflow_execution_id = we.id AND status = 'COMPLETED') as completed_tasks,
    (SELECT count(*) FROM task_executions WHERE workflow_execution_id = we.id AND status = 'FAILED') as failed_tasks,
    (SELECT count(*) FROM user_review_points WHERE workflow_execution_id = we.id) as review_points,
    (SELECT count(*) FROM user_review_points WHERE workflow_execution_id = we.id AND reviewed_at IS NULL) as pending_reviews
FROM
    workflow_executions we
        JOIN workflow_definitions wd ON we.workflow_definition_id = wd.id;

-- Create view for task execution history
CREATE VIEW task_execution_history AS
SELECT
    te.id,
    te.workflow_execution_id,
    we.correlation_id as workflow_correlation_id,
    td.name as task_name,
    td.type as task_type,
    te.status,
    te.started_at,
    te.completed_at,
    te.completed_at - te.started_at as duration,
    te.retry_count,
    te.error_message,
    te.execution_mode
FROM
    task_executions te
        JOIN task_definitions td ON te.task_definition_id = td.id
        JOIN workflow_executions we ON te.workflow_execution_id = we.id
ORDER BY
    te.started_at DESC;

-- Comments for clarity
COMMENT ON TABLE workflow_definitions IS 'Defines workflow templates with versioning';
COMMENT ON TABLE task_definitions IS 'Defines individual tasks within a workflow';
COMMENT ON TABLE workflow_executions IS 'Running instances of workflow definitions';
COMMENT ON TABLE task_executions IS 'Running instances of task definitions';
COMMENT ON TABLE user_review_points IS 'Human review checkpoints in workflows';
COMMENT ON TABLE task_dependencies IS 'Dependencies between tasks for execution ordering';
COMMENT ON TABLE task_definition_config IS 'Key-value configuration for task definitions';
COMMENT ON TABLE workflow_execution_variables IS 'Runtime variables for workflow instances';
COMMENT ON TABLE task_execution_inputs IS 'Input parameters for task execution';
COMMENT ON TABLE task_execution_outputs IS 'Output values from task execution';
