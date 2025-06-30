package com.example.workfloworchestrator.example;

import com.example.workfloworchestrator.json.model.RestApiRequest;
import com.example.workfloworchestrator.json.model.TaskAttributeMapping;
import com.example.workfloworchestrator.json.repository.TaskAttributeMappingRepository;
import com.example.workfloworchestrator.json.service.JsonAttributeExtractionService;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import com.example.workfloworchestrator.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive example demonstrating JSON attribute extraction functionality
 * Shows how to configure mappings and use them in workflow execution
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskAttributeExtractionExample {

    private final WorkflowService workflowService;
    private final WorkflowExecutionService workflowExecutionService;
    private final TaskExecutionService taskExecutionService;
    private final JsonAttributeExtractionService jsonAttributeExtractionService;
    private final TaskAttributeMappingRepository taskAttributeMappingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Run comprehensive example showing JSON attribute extraction
     */
    public void runExample() {
        log.info("\n=== JSON Attribute Extraction Example ===");
        
        try {
            // 1. Create sample workflow with multiple tasks
            WorkflowDefinition workflow = createCustomerOnboardingWorkflow();
            
            // 2. Configure JSON attribute mappings for each task
            configureAttributeMappings(workflow);
            
            // 3. Execute workflow with sample JSON data
            executeWorkflowWithJsonData(workflow);
            
            // 4. Show extraction statistics and validation
            showMappingStatistics(workflow);
            
        } catch (Exception e) {
            log.error("Error running JSON attribute extraction example", e);
        }
    }

    /**
     * Create a customer onboarding workflow with multiple microservice calls
     */
    private WorkflowDefinition createCustomerOnboardingWorkflow() {
        log.info("Creating customer onboarding workflow...");
        
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("customer-onboarding-with-json-extraction");
        workflow.setDescription("Customer onboarding process with JSON attribute extraction");
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        // Task 1: Validate Customer Data
        TaskDefinition validateTask = createTaskDefinition(
            "validate-customer-data",
            "Validate customer information",
            0,
            "https://api.validation-service.com/customers/{customerId}/validate",
            "POST"
        );

        // Task 2: Create Customer Profile
        TaskDefinition createProfileTask = createTaskDefinition(
            "create-customer-profile",
            "Create customer profile in CRM",
            1,
            "https://api.crm-service.com/customers",
            "POST"
        );

        // Task 3: Setup Account
        TaskDefinition setupAccountTask = createTaskDefinition(
            "setup-customer-account",
            "Setup customer account with preferences",
            2,
            "https://api.account-service.com/accounts",
            "POST"
        );

        // Task 4: Send Welcome Email
        TaskDefinition welcomeEmailTask = createTaskDefinition(
            "send-welcome-email",
            "Send personalized welcome email",
            3,
            "https://api.notification-service.com/emails/welcome",
            "POST"
        );

        workflow.addTask(validateTask);
        workflow.addTask(createProfileTask);
        workflow.addTask(setupAccountTask);
        workflow.addTask(welcomeEmailTask);

        WorkflowDefinition savedWorkflow = workflowService.createWorkflowDefinition(workflow);
        log.info("Created workflow: {} with {} tasks", savedWorkflow.getName(), savedWorkflow.getTasks().size());
        
        return savedWorkflow;
    }

    /**
     * Helper method to create task definition
     */
    private TaskDefinition createTaskDefinition(String name, String description, int order, String url, String method) {
        TaskDefinition task = new TaskDefinition();
        task.setName(name);
        task.setDescription(description);
        task.setType("rest-api");
        task.setExecutionOrder(order);
        task.getConfiguration().put("baseUrl", url);
        task.getConfiguration().put("method", method);
        return task;
    }

    /**
     * Configure comprehensive JSON attribute mappings for all tasks
     */
    private void configureAttributeMappings(WorkflowDefinition workflow) {
        log.info("Configuring JSON attribute mappings...");

        // Clear any existing mappings for this example
        workflow.getTasks().forEach(task -> {
            List<TaskAttributeMapping> existing = taskAttributeMappingRepository.findByTaskName(task.getName());
            taskAttributeMappingRepository.deleteAll(existing);
        });

        configureValidationTaskMappings(workflow.getTasks().get(0));
        configureCreateProfileTaskMappings(workflow.getTasks().get(1));
        configureSetupAccountTaskMappings(workflow.getTasks().get(2));
        configureWelcomeEmailTaskMappings(workflow.getTasks().get(3));

        log.info("Configured attribute mappings for all tasks");
    }

    /**
     * Configure mappings for customer validation task
     */
    private void configureValidationTaskMappings(TaskDefinition task) {
        // Extract customer ID for path parameter
        createMapping(task, "/customer/id", "customerId", 
                     TaskAttributeMapping.HttpLocation.PATH_PARAM, true);

        // Extract customer data for request body
        createMapping(task, "/customer/personalInfo/email", "email", 
                     TaskAttributeMapping.HttpLocation.BODY, true);
        
        createMapping(task, "/customer/personalInfo/firstName", "firstName", 
                     TaskAttributeMapping.HttpLocation.BODY, true);
        
        createMapping(task, "/customer/personalInfo/lastName", "lastName", 
                     TaskAttributeMapping.HttpLocation.BODY, true);

        // Extract birth date with transformation
        createMappingWithTransformation(task, "/customer/personalInfo/birthDate", "dateOfBirth",
                                      TaskAttributeMapping.HttpLocation.BODY,
                                      TaskAttributeMapping.TransformationType.DATE_FORMAT,
                                      "{\"inputFormat\":\"yyyy-MM-dd\",\"outputFormat\":\"dd/MM/yyyy\"}", 
                                      false);

        // Add correlation ID header
        createMapping(task, "/request/correlationId", "X-Correlation-ID", 
                     TaskAttributeMapping.HttpLocation.HEADER, false);
    }

    /**
     * Configure mappings for customer profile creation task
     */
    private void configureCreateProfileTaskMappings(TaskDefinition task) {
        // Customer basic info
        createMapping(task, "/customer/personalInfo/email", "emailAddress", 
                     TaskAttributeMapping.HttpLocation.BODY, true);
        
        createMapping(task, "/customer/personalInfo/firstName", "givenName", 
                     TaskAttributeMapping.HttpLocation.BODY, true);
        
        createMapping(task, "/customer/personalInfo/lastName", "familyName", 
                     TaskAttributeMapping.HttpLocation.BODY, true);

        // Extract and transform customer status
        createMappingWithTransformation(task, "/customer/status", "accountStatus",
                                      TaskAttributeMapping.HttpLocation.BODY,
                                      TaskAttributeMapping.TransformationType.VALUE_MAP,
                                      "{\"mappings\":{\"ACTIVE\":\"A\",\"INACTIVE\":\"I\",\"PENDING\":\"P\"},\"defaultValue\":\"P\"}", 
                                      false);

        // Address information
        createMapping(task, "/customer/address/street", "streetAddress", 
                     TaskAttributeMapping.HttpLocation.BODY, false);
        
        createMapping(task, "/customer/address/city", "city", 
                     TaskAttributeMapping.HttpLocation.BODY, false);
        
        createMapping(task, "/customer/address/postalCode", "zipCode", 
                     TaskAttributeMapping.HttpLocation.BODY, false);

        // Phone number
        createMapping(task, "/customer/contactInfo/phone", "phoneNumber", 
                     TaskAttributeMapping.HttpLocation.BODY, false);

        // Request trace ID for correlation
        createMapping(task, "/request/traceId", "X-Trace-ID", 
                     TaskAttributeMapping.HttpLocation.HEADER, false);
    }

    /**
     * Configure mappings for account setup task
     */
    private void configureSetupAccountTaskMappings(TaskDefinition task) {
        // Customer reference
        createMapping(task, "/customer/id", "customerId", 
                     TaskAttributeMapping.HttpLocation.BODY, true);
        
        createMapping(task, "/customer/personalInfo/email", "primaryEmail", 
                     TaskAttributeMapping.HttpLocation.BODY, true);

        // Account preferences
        createMapping(task, "/customer/preferences/currency", "preferredCurrency", 
                     TaskAttributeMapping.HttpLocation.BODY, false);
        
        createMapping(task, "/customer/preferences/language", "preferredLanguage", 
                     TaskAttributeMapping.HttpLocation.BODY, false);
        
        createMapping(task, "/customer/preferences/timezone", "timezone", 
                     TaskAttributeMapping.HttpLocation.BODY, false);

        // Marketing preferences with value mapping
        createMappingWithTransformation(task, "/customer/preferences/marketing", "marketingOptIn",
                                      TaskAttributeMapping.HttpLocation.BODY,
                                      TaskAttributeMapping.TransformationType.VALUE_MAP,
                                      "{\"mappings\":{\"yes\":true,\"no\":false,\"Y\":true,\"N\":false},\"defaultValue\":false}", 
                                      false);

        // Service tier as query parameter
        createMapping(task, "/customer/tier", "serviceTier", 
                     TaskAttributeMapping.HttpLocation.QUERY_PARAM, false);
    }

    /**
     * Configure mappings for welcome email task
     */
    private void configureWelcomeEmailTaskMappings(TaskDefinition task) {
        // Email recipient info
        createMapping(task, "/customer/personalInfo/email", "recipientEmail", 
                     TaskAttributeMapping.HttpLocation.BODY, true);
        
        createMapping(task, "/customer/personalInfo/firstName", "recipientName", 
                     TaskAttributeMapping.HttpLocation.BODY, true);

        // Email template parameters
        createMapping(task, "/customer/preferences/language", "templateLanguage", 
                     TaskAttributeMapping.HttpLocation.BODY, false);

        // Customer ID for personalization
        createMapping(task, "/customer/id", "customerId", 
                     TaskAttributeMapping.HttpLocation.BODY, true);

        // Email priority as query parameter
        createMapping(task, "/customer/tier", "priority", 
                     TaskAttributeMapping.HttpLocation.QUERY_PARAM, false);

        // Campaign tracking header
        createMapping(task, "/request/campaignId", "X-Campaign-ID", 
                     TaskAttributeMapping.HttpLocation.HEADER, false);
    }

    /**
     * Helper method to create simple mapping
     */
    private void createMapping(TaskDefinition task, String sourcePath, String targetField,
                             TaskAttributeMapping.HttpLocation location, boolean required) {
        TaskAttributeMapping mapping = TaskAttributeMapping.builder()
                .taskDefinition(task)
                .taskName(task.getName())
                .sourcePath(sourcePath)
                .targetField(targetField)
                .httpLocation(location)
                .transformationType(TaskAttributeMapping.TransformationType.NONE)
                .required(required)
                .build();

        taskAttributeMappingRepository.save(mapping);
        log.debug("Created mapping: {} -> {} ({})", sourcePath, targetField, location);
    }

    /**
     * Helper method to create mapping with transformation
     */
    private void createMappingWithTransformation(TaskDefinition task, String sourcePath, String targetField,
                                               TaskAttributeMapping.HttpLocation location,
                                               TaskAttributeMapping.TransformationType transformationType,
                                               String transformationConfig, boolean required) {
        TaskAttributeMapping mapping = TaskAttributeMapping.builder()
                .taskDefinition(task)
                .taskName(task.getName())
                .sourcePath(sourcePath)
                .targetField(targetField)
                .httpLocation(location)
                .transformationType(transformationType)
                .transformationConfig(transformationConfig)
                .required(required)
                .build();

        taskAttributeMappingRepository.save(mapping);
        log.debug("Created mapping with transformation: {} -> {} ({}, {})", 
                 sourcePath, targetField, location, transformationType);
    }

    /**
     * Execute workflow with comprehensive sample JSON data
     */
    private void executeWorkflowWithJsonData(WorkflowDefinition workflow) {
        log.info("Executing workflow with sample JSON data...");

        // Create comprehensive sample JSON data
        String sampleJsonData = """
            {
              "request": {
                "correlationId": "req-12345-abcde",
                "traceId": "trace-67890-fghij",
                "campaignId": "welcome-2024-q1",
                "timestamp": "2024-01-15T10:30:00Z"
              },
              "customer": {
                "id": "CUST-2024-001",
                "personalInfo": {
                  "firstName": "John",
                  "lastName": "Doe",
                  "email": "john.doe@example.com",
                  "birthDate": "1985-03-15"
                },
                "address": {
                  "street": "123 Main Street",
                  "city": "New York",
                  "state": "NY",
                  "postalCode": "10001",
                  "country": "US"
                },
                "contactInfo": {
                  "phone": "+1-555-123-4567",
                  "alternateEmail": "john.doe.alt@example.com"
                },
                "preferences": {
                  "currency": "USD",
                  "language": "en-US",
                  "timezone": "America/New_York",
                  "marketing": "yes"
                },
                "status": "ACTIVE",
                "tier": "PREMIUM"
              }
            }
            """;

        // Demonstrate attribute extraction for each task
        demonstrateAttributeExtraction(workflow, sampleJsonData);

        // Execute the actual workflow
        Map<String, String> workflowVariables = new HashMap<>();
        workflowVariables.put("workflowData", sampleJsonData);
        workflowVariables.put("executionType", "json-extraction-demo");

        try {
            WorkflowExecution execution = workflowExecutionService.startWorkflow(
                    workflow.getName(), null, workflowVariables);

            log.info("Started workflow execution with ID: {}", execution.getId());
            log.info("Workflow variables contain JSON data for attribute extraction");

            // Monitor execution briefly
            monitorWorkflowExecution(execution.getId());

        } catch (Exception e) {
            log.error("Error executing workflow", e);
        }
    }

    /**
     * Demonstrate attribute extraction for each task without execution
     */
    private void demonstrateAttributeExtraction(WorkflowDefinition workflow, String jsonData) {
        log.info("\n=== Demonstrating Attribute Extraction ===");

        try {
            var jsonNode = objectMapper.readTree(jsonData);

            for (TaskDefinition task : workflow.getTasks()) {
                log.info("\n--- Task: {} ---", task.getName());

                // Get mappings for this task
                List<TaskAttributeMapping> mappings = jsonAttributeExtractionService.getMappingsForTask(task.getName());
                log.info("Found {} attribute mappings", mappings.size());

                // Build request using JSON extraction
                RestApiRequest extractedRequest = jsonAttributeExtractionService.buildRequest(task, jsonNode);

                // Display extracted components
                displayExtractedRequest(task.getName(), extractedRequest);

                // Show mapping statistics
                Map<String, Object> stats = jsonAttributeExtractionService.getMappingStats(task.getName());
                log.info("Mapping statistics: {}", stats);
            }

        } catch (Exception e) {
            log.error("Error demonstrating attribute extraction", e);
        }
    }

    /**
     * Display the extracted request components
     */
    private void displayExtractedRequest(String taskName, RestApiRequest request) {
        log.info("Extracted request for task '{}':", taskName);

        if (request.hasBody()) {
            log.info("  Body: {}", request.getBody().toPrettyString());
        }

        if (request.hasPathParams()) {
            log.info("  Path Parameters: {}", request.getPathParams());
        }

        if (request.hasQueryParams()) {
            log.info("  Query Parameters: {}", request.getQueryParams());
        }

        if (request.hasHeaders()) {
            log.info("  Headers: {}", request.getHeaders());
        }

        log.info("  Total attributes extracted: {}", request.getTotalAttributeCount());
    }

    /**
     * Show mapping statistics for all tasks
     */
    private void showMappingStatistics(WorkflowDefinition workflow) {
        log.info("\n=== Mapping Statistics Summary ===");

        int totalMappings = 0;
        int totalRequired = 0;
        int totalTransformed = 0;

        for (TaskDefinition task : workflow.getTasks()) {
            Map<String, Object> stats = jsonAttributeExtractionService.getMappingStats(task.getName());
            
            int taskMappings = (Integer) stats.get("totalMappings");
            int taskRequired = (Integer) stats.get("requiredMappings");
            int taskTransformed = (Integer) stats.get("transformedMappings");
            
            totalMappings += taskMappings;
            totalRequired += taskRequired;
            totalTransformed += taskTransformed;

            log.info("Task '{}': {} mappings ({} required, {} transformed)", 
                    task.getName(), taskMappings, taskRequired, taskTransformed);

            @SuppressWarnings("unchecked")
            Map<String, Long> locationCounts = (Map<String, Long>) stats.get("locationCounts");
            log.info("  Location distribution: {}", locationCounts);

            // Validate mappings
            boolean valid = jsonAttributeExtractionService.validateMappingsForTask(task.getName());
            log.info("  Validation status: {}", valid ? "VALID" : "INVALID");
        }

        log.info("\nOverall Statistics:");
        log.info("  Total mappings: {}", totalMappings);
        log.info("  Required mappings: {}", totalRequired);
        log.info("  Transformed mappings: {}", totalTransformed);
        log.info("  Tasks with mappings: {}/{}", workflow.getTasks().size(), workflow.getTasks().size());
    }

    /**
     * Monitor workflow execution briefly
     */
    private void monitorWorkflowExecution(Long executionId) {
        try {
            for (int i = 0; i < 5; i++) { // Monitor for 5 seconds
                Thread.sleep(1000);
                
                WorkflowExecution execution = workflowExecutionService.getWorkflowExecution(executionId);
                log.info("Workflow status: {}", execution.getStatus());

                if (execution.getStatus() == WorkflowStatus.COMPLETED ||
                    execution.getStatus() == WorkflowStatus.FAILED ||
                    execution.getStatus() == WorkflowStatus.CANCELLED) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Error monitoring workflow execution", e);
        }
    }
}
