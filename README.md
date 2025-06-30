# Workflow Orchestrator

A robust, flexible workflow orchestrator built with Spring Boot, Java 21, Gradle, and RabbitMQ.

## Overview

This workflow orchestrator provides a comprehensive framework for defining, executing, and managing complex business workflows. It enables the orchestration of different types of tasks with various execution strategies, supports human review points, and offers flexible retry and error handling capabilities.

## Architecture

![Workflow Orchestrator Architecture](https://i.imgur.com/placeholder_for_architecture_diagram.png)

### Key Components

#### 1. Workflow Definition Model

The workflow definition model consists of a hierarchical structure:

- **WorkflowDefinition**: Defines a workflow with its metadata and collection of tasks
- **TaskDefinition**: Defines individual tasks within a workflow with configuration and execution parameters
- **ExecutionStrategy**: Determines how tasks are executed (sequentially, in parallel, or conditionally)

```java
// Sample workflow definition structure
WorkflowDefinition workflow = new WorkflowDefinition();
workflow.setName("order-processing");
workflow.setDescription("Process customer orders");
workflow.setStrategyType(ExecutionStrategyType.SEQUENTIAL);
```

#### 2. Workflow Engine

The engine is the core component that orchestrates workflow execution:

- Manages workflow state transitions
- Coordinates task execution based on the selected strategy
- Handles errors and failure scenarios
- Provides mechanisms to pause, resume, and cancel workflows

```java
// Executing a workflow
workflowEngine.executeWorkflow(workflowExecution.getId());

// Executing a subset of tasks
workflowEngine.executeTaskSubset(workflowExecutionId, taskIds);
```

#### 3. Task Execution Framework

The task execution framework provides a pluggable mechanism for executing different types of tasks:

- **TaskExecutor Interface**: Common interface for all task executors
- **ExecutionContext**: Passes data between tasks in a workflow
- **Task Types**: HTTP, Database, RabbitMQ, and Custom task executors

```java
// Task executor interface
public interface TaskExecutor {
    Map<String, Object> execute(TaskDefinition taskDefinition, ExecutionContext context) throws TaskExecutionException;
    String getTaskType();
}
```

#### 4. Execution Strategies

Multiple execution strategies are supported:

- **SequentialExecutionStrategy**: Executes tasks one after another
- **ParallelExecutionStrategy**: Executes independent tasks concurrently
- **ConditionalExecutionStrategy**: Determines task execution based on conditions

#### 5. State Management

Workflows and tasks have well-defined states:

- **WorkflowStatus**: CREATED, RUNNING, PAUSED, AWAITING_USER_REVIEW, COMPLETED, FAILED, CANCELLED
- **TaskStatus**: PENDING, RUNNING, COMPLETED, FAILED, SKIPPED, CANCELLED, AWAITING_RETRY

#### 6. User Review Points

The orchestrator supports human-in-the-loop workflows:

- Workflow can pause at designated points for user review
- Users can approve, reject, or request a restart of tasks
- Review decisions influence the workflow path

```java
// Task definition with user review
taskDefinition.setRequireUserReview(true);

// Submitting a user review
userReviewService.submitUserReview(
    reviewPointId, 
    ReviewDecision.APPROVE,
    "john.doe", 
    "Looks good, approved."
);
```

#### 7. Messaging Integration

RabbitMQ integration for:

- Asynchronous task execution
- Communication with external systems
- Event publishing

```java
// Send a task message
rabbitMQSender.sendTaskMessage(taskMessage);

// Listener for task results
@RabbitListener(queues = RabbitMQConfig.WORKFLOW_RESULT_QUEUE)
public void receiveTaskResult(TaskMessage resultMessage) { ... }
```

#### 8. REST API

A comprehensive REST API for:

- Creating and managing workflow definitions
- Starting and controlling workflow executions
- Handling user review points

## Key Programming Concepts

### 1. Domain-Driven Design

The codebase follows Domain-Driven Design principles:

- **Aggregates**: WorkflowDefinition and WorkflowExecution are aggregate roots
- **Value Objects**: TaskDefinition, TaskExecution, etc.
- **Repository Pattern**: Separate repositories for workflow definitions and executions
- **Services**: Domain services for workflow and task operations

### 2. Asynchronous Programming

Extensive use of CompletableFuture for asynchronous operations:

```java
CompletableFuture<WorkflowStatus> futureStatus = strategy.execute(workflowExecution);

futureStatus.thenAccept(status -> {
    // Handle workflow completion
    workflowExecutionService.updateWorkflowExecutionStatus(workflowExecutionId, status);
});
```

### 3. Strategy Pattern

The Strategy pattern is used for execution strategies:

```java
public interface ExecutionStrategy {
    CompletableFuture<WorkflowStatus> execute(WorkflowExecution workflowExecution);
    CompletableFuture<WorkflowStatus> executeSubset(WorkflowExecution workflowExecution, List<Long> taskIds);
}
```

### 4. Observer Pattern

Event publishing follows the Observer pattern:

```java
// Publishing events
eventPublisherService.publishWorkflowStartedEvent(workflowExecution);
eventPublisherService.publishTaskCompletedEvent(taskExecution);
```

### 5. Factory Pattern

Task executors are created based on task type:

```java
private TaskExecutor getTaskExecutor(String taskType) {
    return Optional.ofNullable(taskExecutors.get(taskType))
            .orElseThrow(() -> new TaskExecutionException("No executor found for task type: " + taskType));
}
```

### 6. Dependency Injection

Spring's dependency injection is used throughout the codebase:

```java
@RequiredArgsConstructor
public class WorkflowEngine {
    private final WorkflowExecutionService workflowExecutionService;
    private final TaskExecutionService taskExecutionService;
    private final EventPublisherService eventPublisherService;
    // ...
}
```

### 7. Transactional Boundaries

Spring's @Transactional annotation is used to maintain data consistency:

```java
@Transactional
public WorkflowExecution startWorkflow(String workflowName, String version, Map<String, String> variables) {
    // ...
}
```

### 8. Retry with Exponential Backoff

Sophisticated retry mechanism with exponential backoff:

```java
public long calculateExponentialBackoff(int retryCount) {
    double exponentialPart = Math.pow(multiplier, retryCount);
    long delay = (long) (initialIntervalMs * exponentialPart);
    
    // Add jitter to avoid thundering herd
    double randomFactor = 1.0 + Math.random() * 0.25;
    delay = (long) (delay * randomFactor);
    
    return Math.min(delay, maxIntervalMs);
}
```

### 9. Semantic Versioning

Workflow definitions follow semantic versioning:

```java
public String generateNextVersion(String workflowName) {
    // Logic to generate next version number (e.g., 1.0.0 to 1.0.1)
}
```

## Getting Started

### Prerequisites

- JDK 21 or higher
- PostgreSQL database
- RabbitMQ server

### Building the Project

```bash
./gradlew clean build
```

### Configuration

Configure the application in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow_db
    username: postgres
    password: postgres
  
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

workflow:
  task:
    execution:
      thread-pool-size: 10
    retry:
      max-attempts: 3
      initial-interval: 1000
      multiplier: 2.0
```

### Running the Application

```bash
./gradlew bootRun
```

## Usage Examples

### Creating a Workflow Definition

```java
WorkflowDefinition workflow = new WorkflowDefinition();
workflow.setName("document-approval");
workflow.setDescription("Document approval workflow");
workflow.setStrategyType(ExecutionStrategyType.SEQUENTIAL);

TaskDefinition submitTask = new TaskDefinition();
submitTask.setName("submit-document");
submitTask.setType("http");
submitTask.setExecutionOrder(0);
submitTask.getConfiguration().put("url", "https://api.example.com/documents");
submitTask.getConfiguration().put("method", "POST");

TaskDefinition reviewTask = new TaskDefinition();
reviewTask.setName("review-document");
reviewTask.setType("custom");
reviewTask.setExecutionOrder(1);
reviewTask.setRequireUserReview(true);

workflow.getTasks().add(submitTask);
workflow.getTasks().add(reviewTask);

workflowService.createWorkflowDefinition(workflow);
```

### Starting a Workflow Execution

```java
Map<String, String> variables = new HashMap<>();
variables.put("documentId", "12345");
variables.put("requestor", "john.doe");

workflowExecutionService.startWorkflow("document-approval", null, variables);
```

### Submitting a User Review

```java
userReviewService.submitUserReview(
    reviewPointId,
    UserReviewPoint.ReviewDecision.APPROVE,
    "jane.doe",
    "Document approved after review"
);
```

## Extending the System

### Adding a New Task Executor

1. Create a new implementation of the TaskExecutor interface:

```java
@Component
public class EmailTaskExecutor implements TaskExecutor {
    
    private static final String TASK_TYPE = "email";
    
    @Override
    public Map<String, Object> execute(TaskDefinition taskDefinition, ExecutionContext context) throws TaskExecutionException {
        // Send email implementation
        String to = taskDefinition.getConfiguration().get("to");
        String subject = taskDefinition.getConfiguration().get("subject");
        String body = taskDefinition.getConfiguration().get("body");
        
        // Process variables
        to = processVariables(to, context);
        subject = processVariables(subject, context);
        body = processVariables(body, context);
        
        // Send email logic
        // ...
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sentTo", to);
        return result;
    }
    
    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }
    
    // Helper methods
    // ...
}
```

### Implementing a New Execution Strategy

Create a new implementation of the ExecutionStrategy interface:

```java
@Component
public class PrioritizedExecutionStrategy implements ExecutionStrategy {
    
    // Implementation
    // ...
    
    @Override
    public CompletableFuture<WorkflowStatus> execute(WorkflowExecution workflowExecution) {
        // Custom execution logic based on task priorities
        // ...
    }
    
    @Override
    public CompletableFuture<WorkflowStatus> executeSubset(WorkflowExecution workflowExecution, List<Long> taskIds) {
        // Custom subset execution logic
        // ...
    }
}
```

## JSON Attribute Extraction

The workflow orchestrator includes a powerful JSON attribute extraction feature that enables dynamic request building for REST API tasks based on complex JSON workflow data. This feature eliminates the need for manual request body construction and provides flexible, configuration-driven attribute mapping.

### Key Features

- **RFC 6901 JsonPointer Extraction**: Standards-compliant JSON path extraction with support for nested objects
- **Multi-location Support**: Extract attributes to request body, query parameters, path parameters, and headers
- **Dynamic Transformations**: Built-in transformers for date formatting and value mapping
- **Performance Optimization**: Intelligent caching with configurable TTL using Caffeine
- **Database-driven Configuration**: Hot-configurable attribute mappings stored in the database
- **Graceful Error Handling**: Optional field extraction with fallback, fail-fast for required fields

### Architecture Overview

The JSON attribute extraction system consists of several key components:

```
WorkflowData (JSON) → AttributeExtractor → Transformers → RestApiRequest
                           ↓
                   TaskAttributeMapping (Database Configuration)
```

#### Core Components

1. **AttributeExtractor**: Interface for extraction strategies
2. **JacksonJsonPointerExtractor**: RFC 6901 compliant JsonPointer implementation
3. **AttributeTransformer**: Interface for value transformations
4. **JsonAttributeExtractionService**: Main orchestration service with caching
5. **TaskAttributeMapping**: JPA entity storing extraction configuration

### Configuration Model

Attribute mappings are stored in the database and define how to extract values from workflow JSON:

```java
@Entity
@Table(name = "task_attribute_mappings")
public class TaskAttributeMapping {
    private String taskName;           // Target task name
    private String sourcePath;         // JsonPointer path (e.g., "/customer/email")
    private String targetField;        // Target field name
    private HttpLocation httpLocation; // BODY, QUERY_PARAM, PATH_PARAM, HEADER
    private TransformationType transformationType;
    private String transformationConfig; // JSON configuration for transformers
    private boolean required;           // Fail-fast if extraction fails
}
```

### Usage Examples

#### 1. Simple Field Extraction

Extract customer email from workflow JSON to request body:

```java
// Database configuration
TaskAttributeMapping mapping = new TaskAttributeMapping();
mapping.setTaskName("create-customer");
mapping.setSourcePath("/customer/personalInfo/email");
mapping.setTargetField("email");
mapping.setHttpLocation(HttpLocation.BODY);
mapping.setRequired(true);

// Workflow JSON input
{
  "customer": {
    "personalInfo": {
      "email": "john.doe@example.com",
      "firstName": "John",
      "lastName": "Doe"
    },
    "id": "12345"
  }
}

// Generated request body
{
  "email": "john.doe@example.com"
}
```

#### 2. Path Parameter Extraction

Extract customer ID for URL path substitution:

```java
// Configuration
mapping.setSourcePath("/customer/id");
mapping.setTargetField("customerId");
mapping.setHttpLocation(HttpLocation.PATH_PARAM);

// URL template: /api/customers/{customerId}/profile
// Result: /api/customers/12345/profile
```

#### 3. Date Format Transformation

Transform date values for API compatibility:

```java
// Configuration
mapping.setSourcePath("/customer/birthDate");
mapping.setTargetField("dateOfBirth");
mapping.setTransformationType(TransformationType.DATE_FORMAT);
mapping.setTransformationConfig("""
{
  "inputFormat": "yyyy-MM-dd",
  "outputFormat": "dd/MM/yyyy",
  "timeZone": "UTC"
}
""");

// Input: "1990-05-15"
// Output: "15/05/1990"
```

#### 4. Value Mapping Transformation

Map status codes for microservice expectations:

```java
// Configuration
mapping.setSourcePath("/customer/status");
mapping.setTargetField("statusCode");
mapping.setTransformationType(TransformationType.VALUE_MAP);
mapping.setTransformationConfig("""
{
  "mappings": {
    "ACTIVE": "A",
    "INACTIVE": "I",
    "SUSPENDED": "S"
  },
  "defaultValue": "U",
  "strict": false
}
""");

// Input: "ACTIVE"
// Output: "A"
```

#### 5. Header Extraction

Extract correlation IDs for request headers:

```java
// Configuration
mapping.setSourcePath("/request/traceId");
mapping.setTargetField("X-Trace-ID");
mapping.setHttpLocation(HttpLocation.HEADER);

// Generated headers
{
  "X-Trace-ID": "abc-123-def-456",
  "Content-Type": "application/json"
}
```

### Advanced Configuration

#### Complex Nested Extraction

```java
// Workflow JSON
{
  "order": {
    "customer": {
      "billing": {
        "address": {
          "street": "123 Main St",
          "city": "New York",
          "zipCode": "10001"
        }
      }
    },
    "items": [
      {"id": "item1", "quantity": 2},
      {"id": "item2", "quantity": 1}
    ]
  }
}

// Multiple attribute mappings
"/order/customer/billing/address/street" → "billingStreet" (BODY)
"/order/customer/billing/address/city" → "billingCity" (BODY)
"/order/customer/billing/address/zipCode" → "zipCode" (QUERY_PARAM)
"/order/items/0/id" → "primaryItemId" (PATH_PARAM)
```

#### Conditional Extraction

```java
// Configuration with conditional logic
mapping.setTransformationConfig("""
{
  "condition": "notNull",
  "defaultValue": "unknown",
  "fallbackPath": "/customer/defaultEmail"
}
""");
```

### Integration with Workflow Engine

The extraction service integrates seamlessly with the existing workflow engine:

```java
@Service
public class RestApiTaskExecutor extends AbstractTaskExecutor {
    
    @Override
    protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) {
        // Check if JSON extraction is configured
        RestApiRequest extractedRequest = jsonAttributeExtractionService
            .buildRequest(taskDefinition.getName(), context.getWorkflowData());
        
        if (extractedRequest != null) {
            // Use extracted request components
            return executeWithExtractedRequest(extractedRequest, taskDefinition, context);
        } else {
            // Fall back to existing configuration-based approach
            return executeWithConfiguration(taskDefinition, context);
        }
    }
}
```

### Performance Characteristics

The JSON attribute extraction system is designed for high performance:

- **Sub-millisecond extraction**: Optimized JsonPointer implementation
- **Intelligent caching**: Caffeine cache with configurable TTL (default: 30 minutes)
- **Minimal memory footprint**: Streaming JSON processing where possible
- **Scalable**: Supports workflows with thousands of attributes

### Management API

REST endpoints for managing attribute mappings:

```java
// Get mappings for a specific task
GET /api/v1/attribute-mappings/task/{taskName}

// Create new mapping
POST /api/v1/attribute-mappings
{
  "taskName": "user-service",
  "sourcePath": "/user/profile/email",
  "targetField": "userEmail",
  "httpLocation": "BODY",
  "required": true
}

// Update existing mapping
PUT /api/v1/attribute-mappings/{id}

// Delete mapping
DELETE /api/v1/attribute-mappings/{id}
```

### Error Handling

The system provides comprehensive error handling:

```java
// Required field missing
{
  "error": "EXTRACTION_FAILED",
  "message": "Required field 'customerId' not found at path '/customer/id'",
  "taskName": "create-customer",
  "sourcePath": "/customer/id"
}

// Transformation error
{
  "error": "TRANSFORMATION_FAILED",
  "message": "Date format transformation failed: Invalid date format",
  "inputValue": "invalid-date",
  "transformationType": "DATE_FORMAT"
}

// Optional field handling
// Missing optional fields are skipped silently
// Default values are applied when configured
```

### Testing Support

Comprehensive testing utilities for attribute extraction:

```java
@Test
void shouldExtractMultipleAttributesForComplexWorkflow() {
    // Given: complex workflow JSON
    JsonNode workflowData = createComplexWorkflowData();
    
    // When: extracting attributes for microservice call
    RestApiRequest request = jsonAttributeExtractionService
        .buildRequest("user-service", workflowData);
    
    // Then: verify all expected attributes extracted correctly
    assertThat(request.getBody())
        .hasFieldOrPropertyWithValue("email", "john@example.com")
        .hasFieldOrPropertyWithValue("firstName", "John");
    
    assertThat(request.getPathParams())
        .containsEntry("userId", "12345");
        
    assertThat(request.getHeaders())
        .containsEntry("X-Correlation-ID", "abc-123");
}
```

### Migration and Backward Compatibility

The JSON attribute extraction feature is designed for seamless integration:

- **Backward compatible**: Existing workflows continue to work unchanged
- **Gradual adoption**: Can be enabled per task as needed
- **Fallback mechanism**: Automatic fallback to configuration-based approach
- **Zero downtime**: Hot configuration updates without restart

## Advanced Features

### Complex Workflow Patterns

The system supports complex workflow patterns:

- **Sequential Steps**: Tasks executed in a defined order
- **Parallel Processing**: Multiple tasks executed concurrently
- **Conditional Branches**: Based on task outcomes or workflow context
- **Error Handling Paths**: Special flows for handling errors
- **User Decision Points**: Human-in-the-loop decision making

### Event Handling and Integration

The workflow orchestrator can integrate with external systems through:

- Event publishing for workflow and task state changes
- RabbitMQ messaging for asynchronous communication
- Extensible event handlers for custom integrations

### Monitoring and Management

The system provides:

- REST API for monitoring workflow status
- Detection of stuck workflows
- Retry mechanisms for failed tasks
- Historical tracking of workflow executions

## Conclusion

This workflow orchestrator demonstrates advanced Java and Spring Boot concepts while providing a flexible framework for business process automation. The modular design allows for easy extension to meet specific business requirements, and the integration capabilities enable it to work within a larger enterprise ecosystem.

## License

[MIT License](LICENSE)
