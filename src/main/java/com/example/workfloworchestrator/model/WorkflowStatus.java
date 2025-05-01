package com.example.workfloworchestrator.model;

public enum WorkflowStatus {
    CREATED,
    RUNNING,
    PAUSED,
    AWAITING_USER_REVIEW,
    COMPLETED,
    FAILED,
    CANCELLED
}
