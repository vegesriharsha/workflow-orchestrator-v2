-- src/test/resources/schema-mssql.sql
-- Microsoft SQL Server compatible schema for workflow orchestrator tests

-- Drop existing tables if they exist (for test cleanup)
IF OBJECT_ID('dbo.user_review_points', 'U') IS NOT NULL DROP TABLE dbo.user_review_points;
IF OBJECT_ID('dbo.task_execution_outputs', 'U') IS NOT NULL DROP TABLE dbo.task_execution_outputs;
IF OBJECT_ID('dbo.task_execution_inputs', 'U') IS NOT NULL DROP TABLE dbo.task_execution_inputs;
IF OBJECT_ID('dbo.task_executions', 'U') IS NOT NULL DROP TABLE dbo.task_executions;
IF OBJECT_ID('dbo.workflow_execution_variables', 'U') IS NOT NULL DROP TABLE dbo.workflow_execution_variables;
IF OBJECT_ID('dbo.workflow_executions', 'U') IS NOT NULL DROP TABLE dbo.workflow_executions;
IF OBJECT_ID('dbo.task_definition_config', 'U') IS NOT NULL DROP TABLE dbo.task_definition_config;
IF OBJECT_ID('dbo.task_dependencies', 'U') IS NOT NULL DROP TABLE dbo.task_dependencies;
IF OBJECT_ID('dbo.task_definitions', 'U') IS NOT NULL DROP TABLE dbo.task_definitions;
IF OBJECT_ID('dbo.workflow_definitions', 'U') IS NOT NULL DROP TABLE dbo.workflow_definitions;

-- Workflow Definitions
CREATE TABLE workflow_definitions (
                                      id BIGINT IDENTITY(1,1) PRIMARY KEY,
                                      name NVARCHAR(255) NOT NULL,
                                      description NVARCHAR(MAX),
                                      version NVARCHAR(50) NOT NULL,
                                      created_at DATETIME2 NOT NULL,
                                      updated_at DATETIME2,
                                      strategy_type NVARCHAR(50) NOT NULL DEFAULT 'SEQUENTIAL',
                                      CONSTRAINT UQ_workflow_definitions_name_version UNIQUE (name, version)
);

-- Task Definitions
CREATE TABLE task_definitions (
                                  id BIGINT IDENTITY(1,1) PRIMARY KEY,
                                  workflow_definition_id BIGINT,
                                  name NVARCHAR(255) NOT NULL,
                                  description NVARCHAR(MAX),
                                  type NVARCHAR(100) NOT NULL,
                                  execution_order INT NOT NULL,
                                  retry_limit INT DEFAULT 3,
                                  timeout_seconds INT DEFAULT 60,
                                  execution_mode NVARCHAR(50) NOT NULL DEFAULT 'API',
                                  require_user_review BIT DEFAULT 0,
                                  conditional_expression NVARCHAR(MAX),
                                  next_task_on_success BIGINT,
                                  next_task_on_failure BIGINT,
                                  CONSTRAINT FK_task_definitions_workflow FOREIGN KEY (workflow_definition_id)
                                      REFERENCES workflow_definitions(id) ON DELETE CASCADE
);

-- Task Dependencies
CREATE TABLE task_dependencies (
                                   task_definition_id BIGINT NOT NULL,
                                   dependency_task_id BIGINT NOT NULL,
                                   CONSTRAINT PK_task_dependencies PRIMARY KEY (task_definition_id, dependency_task_id),
                                   CONSTRAINT FK_task_dependencies_task FOREIGN KEY (task_definition_id)
                                       REFERENCES task_definitions(id) ON DELETE CASCADE
);

-- Task Definition Configuration
CREATE TABLE task_definition_config (
                                        task_definition_id BIGINT NOT NULL,
                                        config_key NVARCHAR(255) NOT NULL,
                                        config_value NVARCHAR(MAX),
                                        CONSTRAINT PK_task_definition_config PRIMARY KEY (task_definition_id, config_key),
                                        CONSTRAINT FK_task_definition_config_task FOREIGN KEY (task_definition_id)
                                            REFERENCES task_definitions(id) ON DELETE CASCADE
);

-- Workflow Executions
CREATE TABLE workflow_executions (
                                     id BIGINT IDENTITY(1,1) PRIMARY KEY,
                                     workflow_definition_id BIGINT NOT NULL,
                                     correlation_id NVARCHAR(255) NOT NULL UNIQUE,
                                     status NVARCHAR(50) NOT NULL,
                                     started_at DATETIME2,
                                     completed_at DATETIME2,
                                     current_task_index INT,
                                     retry_count INT DEFAULT 0,
                                     error_message NVARCHAR(MAX),
                                     CONSTRAINT FK_workflow_executions_definition FOREIGN KEY (workflow_definition_id)
                                         REFERENCES workflow_definitions(id)
);

-- Workflow Execution Variables
CREATE TABLE workflow_execution_variables (
                                              workflow_execution_id BIGINT NOT NULL,
                                              variable_key NVARCHAR(255) NOT NULL,
                                              variable_value NVARCHAR(MAX),
                                              CONSTRAINT PK_workflow_execution_variables PRIMARY KEY (workflow_execution_id, variable_key),
                                              CONSTRAINT FK_workflow_execution_variables_execution FOREIGN KEY (workflow_execution_id)
                                                  REFERENCES workflow_executions(id) ON DELETE CASCADE
);

-- Task Executions
CREATE TABLE task_executions (
                                 id BIGINT IDENTITY(1,1) PRIMARY KEY,
                                 workflow_execution_id BIGINT NOT NULL,
                                 task_definition_id BIGINT NOT NULL,
                                 status NVARCHAR(50) NOT NULL,
                                 started_at DATETIME2,
                                 completed_at DATETIME2,
                                 execution_mode NVARCHAR(50) NOT NULL,
                                 retry_count INT DEFAULT 0,
                                 next_retry_at DATETIME2,
                                 error_message NVARCHAR(MAX),
                                 CONSTRAINT FK_task_executions_workflow FOREIGN KEY (workflow_execution_id)
                                     REFERENCES workflow_executions(id) ON DELETE CASCADE,
                                 CONSTRAINT FK_task_executions_definition FOREIGN KEY (task_definition_id)
                                     REFERENCES task_definitions(id)
);

-- Task Execution Inputs
CREATE TABLE task_execution_inputs (
                                       task_execution_id BIGINT NOT NULL,
                                       input_key NVARCHAR(255) NOT NULL,
                                       input_value NVARCHAR(MAX),
                                       CONSTRAINT PK_task_execution_inputs PRIMARY KEY (task_execution_id, input_key),
                                       CONSTRAINT FK_task_execution_inputs_execution FOREIGN KEY (task_execution_id)
                                           REFERENCES task_executions(id) ON DELETE CASCADE
);

-- Task Execution Outputs
CREATE TABLE task_execution_outputs (
                                        task_execution_id BIGINT NOT NULL,
                                        output_key NVARCHAR(255) NOT NULL,
                                        output_value NVARCHAR(MAX),
                                        CONSTRAINT PK_task_execution_outputs PRIMARY KEY (task_execution_id, output_key),
                                        CONSTRAINT FK_task_execution_outputs_execution FOREIGN KEY (task_execution_id)
                                            REFERENCES task_executions(id) ON DELETE CASCADE
);

-- User Review Points
CREATE TABLE user_review_points (
                                    id BIGINT IDENTITY(1,1) PRIMARY KEY,
                                    workflow_execution_id BIGINT NOT NULL,
                                    task_execution_id BIGINT NOT NULL,
                                    created_at DATETIME2 NOT NULL,
                                    reviewed_at DATETIME2,
                                    reviewer NVARCHAR(255),
                                    comment NVARCHAR(MAX),
                                    decision NVARCHAR(50),
                                    CONSTRAINT FK_user_review_points_workflow FOREIGN KEY (workflow_execution_id)
                                        REFERENCES workflow_executions(id) ON DELETE CASCADE,
                                    CONSTRAINT FK_user_review_points_task FOREIGN KEY (task_execution_id)
                                        REFERENCES task_executions(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX IX_workflow_definitions_name ON workflow_definitions(name);
CREATE INDEX IX_task_definitions_workflow ON task_definitions(workflow_definition_id);
CREATE INDEX IX_task_definitions_order ON task_definitions(workflow_definition_id, execution_order);
CREATE INDEX IX_workflow_executions_status ON workflow_executions(status);
CREATE INDEX IX_workflow_executions_correlation ON workflow_executions(correlation_id);
CREATE INDEX IX_workflow_executions_definition ON workflow_executions(workflow_definition_id);
CREATE INDEX IX_task_executions_workflow ON task_executions(workflow_execution_id);
CREATE INDEX IX_task_executions_status ON task_executions(status);
CREATE INDEX IX_task_executions_retry ON task_executions(next_retry_at) WHERE status = 'AWAITING_RETRY';
CREATE INDEX IX_user_review_points_workflow ON user_review_points(workflow_execution_id);
CREATE INDEX IX_user_review_points_task ON user_review_points(task_execution_id);

-- Create views for test environment
GO

-- View for active workflows
CREATE VIEW active_workflows AS
SELECT
    we.id,
    wd.name as workflow_name,
    wd.version,
    we.correlation_id,
    we.status,
    we.started_at,
    DATEDIFF(second, we.started_at, GETDATE()) as duration_seconds,
    we.current_task_index,
    (SELECT COUNT(*) FROM task_executions te WHERE te.workflow_execution_id = we.id) as total_tasks,
    (SELECT COUNT(*) FROM task_executions te WHERE te.workflow_execution_id = we.id AND te.status = 'COMPLETED') as completed_tasks
FROM
    workflow_executions we
        JOIN
    workflow_definitions wd ON we.workflow_definition_id = wd.id
WHERE
    we.status IN ('CREATED', 'RUNNING', 'PAUSED', 'AWAITING_USER_REVIEW');

GO

-- View for pending user reviews
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
    DATEDIFF(second, urp.created_at, GETDATE()) as pending_duration_seconds
FROM
    user_review_points urp
        JOIN
    workflow_executions we ON urp.workflow_execution_id = we.id
        JOIN
    workflow_definitions wd ON we.workflow_definition_id = wd.id
        JOIN
    task_executions te ON urp.task_execution_id = te.id
        JOIN
    task_definitions td ON te.task_definition_id = td.id
WHERE
    urp.reviewed_at IS NULL;

GO
