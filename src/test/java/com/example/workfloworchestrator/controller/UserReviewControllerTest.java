package com.example.workfloworchestrator.controller;

import com.example.workfloworchestrator.model.UserReviewPoint;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.service.UserReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserReviewControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserReviewService userReviewService;

    @InjectMocks
    private UserReviewController userReviewController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userReviewController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void submitReview_ShouldReturnWorkflowExecution() throws Exception {
        // Arrange
        Long reviewPointId = 1L;
        UserReviewController.ReviewRequest request = new UserReviewController.ReviewRequest();
        request.setDecision(UserReviewPoint.ReviewDecision.APPROVE);
        request.setReviewer("testUser");
        request.setComment("Looks good");

        WorkflowExecution mockExecution = new WorkflowExecution();
        mockExecution.setId(1L);

        when(userReviewService.submitUserReview(
                eq(reviewPointId),
                eq(request.getDecision()),
                eq(request.getReviewer()),
                eq(request.getComment())
        )).thenReturn(mockExecution);

        // Act & Assert
        mockMvc.perform(post("/api/reviews/{reviewPointId}", reviewPointId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void submitReview_WithRejectDecision_ShouldReturnWorkflowExecution() throws Exception {
        // Arrange
        Long reviewPointId = 2L;
        UserReviewController.ReviewRequest request = new UserReviewController.ReviewRequest();
        request.setDecision(UserReviewPoint.ReviewDecision.REJECT);
        request.setReviewer("adminUser");
        request.setComment("Changes needed");

        WorkflowExecution mockExecution = new WorkflowExecution();
        mockExecution.setId(2L);

        when(userReviewService.submitUserReview(anyLong(), any(), anyString(), anyString()))
                .thenReturn(mockExecution);

        // Act & Assert
        mockMvc.perform(post("/api/reviews/{reviewPointId}", reviewPointId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L));
    }
}
