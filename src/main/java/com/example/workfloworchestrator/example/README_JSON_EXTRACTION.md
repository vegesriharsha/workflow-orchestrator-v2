# JSON Attribute Extraction Example

This example demonstrates the powerful JSON attribute extraction feature in the workflow orchestrator, showing how to dynamically extract data from complex JSON payloads and map them to REST API requests for microservice calls.

## Overview

The JSON attribute extraction feature allows you to:

- **Extract specific attributes** from complex JSON data using JsonPointer paths
- **Transform extracted values** using built-in transformers (date formatting, value mapping)
- **Map attributes to different HTTP locations** (body, query parameters, path parameters, headers)
- **Configure required vs optional** field extraction
- **Cache extraction configurations** for optimal performance
- **Validate mappings** before execution

## Example Scenario: Customer Onboarding

The example demonstrates a customer onboarding workflow with 4 microservice calls:

1. **Validate Customer Data** - Validates customer information
2. **Create Customer Profile** - Creates customer profile in CRM
3. **Setup Customer Account** - Sets up account with preferences
4. **Send Welcome Email** - Sends personalized welcome email

## Sample JSON Input

```json
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
```

## Attribute Mapping Examples

### 1. Validate Customer Data Task

**Endpoint**: `POST https://api.validation-service.com/customers/{customerId}/validate`

**Mappings**:
- `/customer/id` → Path Parameter `customerId` (required)
- `/customer/personalInfo/email` → Body `email` (required)
- `/customer/personalInfo/firstName` → Body `firstName` (required)
- `/customer/personalInfo/lastName` → Body `lastName` (required)
- `/customer/personalInfo/birthDate` → Body `dateOfBirth` (date format transformation)
- `/request/correlationId` → Header `X-Correlation-ID`

**Generated Request**:
```http
POST https://api.validation-service.com/customers/CUST-2024-001/validate
X-Correlation-ID: req-12345-abcde
Content-Type: application/json

{
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "dateOfBirth": "15/03/1985"
}
```

### 2. Create Customer Profile Task

**Endpoint**: `POST https://api.crm-service.com/customers`

**Mappings**:
- `/customer/personalInfo/email` → Body `emailAddress` (required)
- `/customer/personalInfo/firstName` → Body `givenName` (required)
- `/customer/personalInfo/lastName` → Body `familyName` (required)
- `/customer/status` → Body `accountStatus` (value mapping: ACTIVE→A, INACTIVE→I, PENDING→P)
- `/customer/address/street` → Body `streetAddress`
- `/customer/address/city` → Body `city`
- `/customer/address/postalCode` → Body `zipCode`
- `/customer/contactInfo/phone` → Body `phoneNumber`
- `/request/traceId` → Header `X-Trace-ID`

**Generated Request**:
```http
POST https://api.crm-service.com/customers
X-Trace-ID: trace-67890-fghij
Content-Type: application/json

{
  "emailAddress": "john.doe@example.com",
  "givenName": "John",
  "familyName": "Doe",
  "accountStatus": "A",
  "streetAddress": "123 Main Street",
  "city": "New York",
  "zipCode": "10001",
  "phoneNumber": "+1-555-123-4567"
}
```

### 3. Setup Customer Account Task

**Endpoint**: `POST https://api.account-service.com/accounts?serviceTier=PREMIUM`

**Mappings**:
- `/customer/id` → Body `customerId` (required)
- `/customer/personalInfo/email` → Body `primaryEmail` (required)
- `/customer/preferences/currency` → Body `preferredCurrency`
- `/customer/preferences/language` → Body `preferredLanguage`
- `/customer/preferences/timezone` → Body `timezone`
- `/customer/preferences/marketing` → Body `marketingOptIn` (value mapping: yes→true, no→false)
- `/customer/tier` → Query Parameter `serviceTier`

**Generated Request**:
```http
POST https://api.account-service.com/accounts?serviceTier=PREMIUM
Content-Type: application/json

{
  "customerId": "CUST-2024-001",
  "primaryEmail": "john.doe@example.com",
  "preferredCurrency": "USD",
  "preferredLanguage": "en-US",
  "timezone": "America/New_York",
  "marketingOptIn": true
}
```

### 4. Send Welcome Email Task

**Endpoint**: `POST https://api.notification-service.com/emails/welcome?priority=PREMIUM`

**Mappings**:
- `/customer/personalInfo/email` → Body `recipientEmail` (required)
- `/customer/personalInfo/firstName` → Body `recipientName` (required)
- `/customer/preferences/language` → Body `templateLanguage`
- `/customer/id` → Body `customerId` (required)
- `/customer/tier` → Query Parameter `priority`
- `/request/campaignId` → Header `X-Campaign-ID`

**Generated Request**:
```http
POST https://api.notification-service.com/emails/welcome?priority=PREMIUM
X-Campaign-ID: welcome-2024-q1
Content-Type: application/json

{
  "recipientEmail": "john.doe@example.com",
  "recipientName": "John",
  "templateLanguage": "en-US",
  "customerId": "CUST-2024-001"
}
```

## Key Features Demonstrated

### 1. JsonPointer Path Extraction
- **Simple paths**: `/customer/id`
- **Nested paths**: `/customer/personalInfo/firstName`
- **Deep nesting**: `/customer/address/postalCode`

### 2. HTTP Location Mapping
- **BODY**: Most common for REST API payloads
- **PATH_PARAM**: For URL path substitution (`{customerId}`)
- **QUERY_PARAM**: For URL query parameters (`?serviceTier=PREMIUM`)
- **HEADER**: For correlation and tracking headers

### 3. Value Transformations

#### Date Format Transformation
```json
{
  "inputFormat": "yyyy-MM-dd",
  "outputFormat": "dd/MM/yyyy"
}
```
Transforms `"1985-03-15"` → `"15/03/1985"`

#### Value Mapping Transformation
```json
{
  "mappings": {
    "ACTIVE": "A",
    "INACTIVE": "I", 
    "PENDING": "P"
  },
  "defaultValue": "P"
}
```
Transforms `"ACTIVE"` → `"A"`

```json
{
  "mappings": {
    "yes": true,
    "no": false,
    "Y": true,
    "N": false
  },
  "defaultValue": false
}
```
Transforms `"yes"` → `true`

### 4. Required vs Optional Fields
- **Required fields**: Cause workflow failure if not found
- **Optional fields**: Gracefully skipped if not found
- **Validation**: Performed before workflow execution

### 5. Performance Optimization
- **Mapping caching**: Configurations cached by task name
- **Request caching**: Built requests cached by task + JSON hash
- **Lazy evaluation**: Mappings loaded only when needed

## Running the Example

### Option 1: Through WorkflowRunner
```bash
./gradlew bootRun --args="--spring.profiles.active=example"
# Choose option 5: JSON Attribute Extraction Workflow
```

### Option 2: Direct Class Execution
```bash
./gradlew bootRun --args="--spring.main.web-application-type=none"
# Then call TaskAttributeExtractionExample.runExample()
```

## Example Output

```
=== JSON Attribute Extraction Example ===
Creating customer onboarding workflow...
Created workflow: customer-onboarding-with-json-extraction with 4 tasks
Configuring JSON attribute mappings...
Configured attribute mappings for all tasks

=== Demonstrating Attribute Extraction ===

--- Task: validate-customer-data ---
Found 6 attribute mappings
Extracted request for task 'validate-customer-data':
  Body: {
    "email" : "john.doe@example.com",
    "firstName" : "John",
    "lastName" : "Doe",
    "dateOfBirth" : "15/03/1985"
  }
  Path Parameters: {customerId=CUST-2024-001}
  Headers: {X-Correlation-ID=req-12345-abcde}
  Total attributes extracted: 6
Mapping statistics: {totalMappings=6, requiredMappings=4, transformedMappings=1, locationCounts={BODY=4, PATH_PARAM=1, HEADER=1}}

--- Task: create-customer-profile ---
Found 9 attribute mappings
Extracted request for task 'create-customer-profile':
  Body: {
    "emailAddress" : "john.doe@example.com",
    "givenName" : "John",
    "familyName" : "Doe",
    "accountStatus" : "A",
    "streetAddress" : "123 Main Street",
    "city" : "New York",
    "zipCode" : "10001",
    "phoneNumber" : "+1-555-123-4567"
  }
  Headers: {X-Trace-ID=trace-67890-fghij}
  Total attributes extracted: 9
Mapping statistics: {totalMappings=9, requiredMappings=3, transformedMappings=1, locationCounts={BODY=8, HEADER=1}}

=== Mapping Statistics Summary ===
Task 'validate-customer-data': 6 mappings (4 required, 1 transformed)
  Location distribution: {BODY=4, PATH_PARAM=1, HEADER=1}
  Validation status: VALID
Task 'create-customer-profile': 9 mappings (3 required, 1 transformed)
  Location distribution: {BODY=8, HEADER=1}
  Validation status: VALID
Task 'setup-customer-account': 7 mappings (2 required, 1 transformed)
  Location distribution: {BODY=6, QUERY_PARAM=1}
  Validation status: VALID
Task 'send-welcome-email': 6 mappings (3 required, 0 transformed)
  Location distribution: {BODY=4, QUERY_PARAM=1, HEADER=1}
  Validation status: VALID

Overall Statistics:
  Total mappings: 28
  Required mappings: 12
  Transformed mappings: 3
  Tasks with mappings: 4/4
```

## Advanced Configuration

### Custom Transformers
You can create custom transformers by implementing `AttributeTransformer`:

```java
@Component("customTransformer")
public class CustomTransformer implements AttributeTransformer {
    @Override
    public Object transform(Object value, String config) {
        // Your custom transformation logic
        return transformedValue;
    }
}
```

### Database Schema
The feature uses the `task_attribute_mappings` table:

```sql
CREATE TABLE task_attribute_mappings (
    id BIGSERIAL PRIMARY KEY,
    task_definition_id BIGINT,
    task_name VARCHAR(255) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    target_field VARCHAR(255) NOT NULL,
    http_location VARCHAR(50) NOT NULL,
    transformation_type VARCHAR(50) DEFAULT 'NONE',
    transformation_config TEXT,
    required BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_name (task_name)
);
```

### REST API Management
Attribute mappings can be managed via REST APIs:

```http
GET /api/v1/attribute-mappings/task/{taskName}
POST /api/v1/attribute-mappings
PUT /api/v1/attribute-mappings/{id}
DELETE /api/v1/attribute-mappings/{id}
```

## Benefits

1. **Dynamic Request Building**: No manual request construction needed
2. **Configuration-Driven**: All mappings stored in database, hot-configurable
3. **Performance Optimized**: Intelligent caching prevents repeated JSON parsing
4. **Type Safety**: JsonPointer provides compile-time path validation
5. **Backward Compatible**: Existing workflows continue unchanged
6. **Flexible Transformations**: Support for complex data transformations
7. **Error Handling**: Graceful degradation for optional fields, fail-fast for required
8. **Monitoring**: Built-in metrics and validation
9. **Scalable**: Supports complex workflows with thousands of attributes
10. **Maintainable**: Clean separation of concerns and comprehensive testing

## Best Practices

1. **Use required fields sparingly** - Only mark truly essential fields as required
2. **Cache configuration** - Leverage the built-in caching for performance
3. **Validate mappings** - Always validate before production deployment
4. **Monitor extraction metrics** - Track success/failure rates
5. **Document transformations** - Clear transformation configuration documentation
6. **Test with real data** - Use production-like JSON structures for testing
7. **Version mappings** - Consider versioning strategies for mapping changes
8. **Handle errors gracefully** - Implement proper error handling for edge cases

This example showcases the full power of the JSON attribute extraction feature, demonstrating how it can significantly simplify microservice orchestration by eliminating manual request construction and enabling dynamic, configuration-driven data mapping.
