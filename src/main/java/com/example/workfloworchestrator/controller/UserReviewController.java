package com.example.workfloworchestrator.controller;

import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.TaskExecution;
import com.example.workfloworchestrator.model.TaskStatus;
import com.example.workfloworchestrator.model.UserReviewPoint;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.UserReviewService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * REST controller for user review operations
 */
@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class UserReviewController {

    private final UserReviewService userReviewService;

    @PostMapping("/{reviewPointId}")
    public ResponseEntity<WorkflowExecution> submitReview(
            @PathVariable Long reviewPointId,
            @RequestBody ReviewRequest request) {

        return ResponseEntity.ok(userReviewService.submitUserReview(
                reviewPointId,
                request.getDecision(),
                request.getReviewer(),
                request.getComment()));
    }

    @Data
    public static class ReviewRequest {
        private UserReviewPoint.ReviewDecision decision;
        private String reviewer;
        private String comment;
    }
}
