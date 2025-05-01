package com.example.workfloworchestrator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_review_points")
public class UserReviewPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_execution_id")
    private Long taskExecutionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewer")
    private String reviewer;

    @Column(name = "comment")
    private String comment;

    @Column(name = "decision")
    @Enumerated(EnumType.STRING)
    private ReviewDecision decision;

    public enum ReviewDecision {
        APPROVE,          // Approve task and continue
        REJECT,           // Reject task and fail
        RESTART,          // Restart this task
    }
}
