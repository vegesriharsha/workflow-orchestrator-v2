package com.example.workfloworchestrator.event;

/**
 * Enum for task event types
 */
public enum TaskEventType {
    CREATED,
    STARTED,
    COMPLETED,
    FAILED,
    SKIPPED,
    RETRY_SCHEDULED
}
