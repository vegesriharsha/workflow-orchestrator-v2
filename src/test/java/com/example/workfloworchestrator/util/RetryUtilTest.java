package com.example.workfloworchestrator.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class RetryUtilTest {

    private RetryUtil retryUtil;

    @BeforeEach
    void setUp() {
        retryUtil = new RetryUtil();

        // Set private fields using reflection
        ReflectionTestUtils.setField(retryUtil, "initialIntervalMs", 1000L);
        ReflectionTestUtils.setField(retryUtil, "multiplier", 2.0);
        ReflectionTestUtils.setField(retryUtil, "maxIntervalMs", 10000L);
    }

    @Test
    void calculateNextRetryTime_ShouldReturnFutureTime() {
        // Act
        LocalDateTime nextRetryTime = retryUtil.calculateNextRetryTime(0);

        // Assert
        assertThat(nextRetryTime).isAfter(LocalDateTime.now());
    }

    @Test
    void calculateExponentialBackoff_WithZeroRetries_ShouldReturnInitialInterval() {
        // Act
        long delay = retryUtil.calculateExponentialBackoff(0);

        // Assert (allowing for jitter)
        assertThat(delay).isBetween(1000L, 1250L);
    }

    @Test
    void calculateExponentialBackoff_WithIncrementingRetries_ShouldIncreaseExponentially() {
        // Act
        long delay0 = retryUtil.calculateExponentialBackoff(0);
        long delay1 = retryUtil.calculateExponentialBackoff(1);
        long delay2 = retryUtil.calculateExponentialBackoff(2);

        // Assert (rough comparison due to jitter)
        assertThat(delay1).isGreaterThan(delay0);
        assertThat(delay2).isGreaterThan(delay1);
    }

    @Test
    void calculateExponentialBackoff_WithManyRetries_ShouldNotExceedMaxInterval() {
        // Act
        long delay = retryUtil.calculateExponentialBackoff(10); // High retry count

        // Assert
        assertThat(delay).isLessThanOrEqualTo(10000L); // MaxInterval
    }

    @Test
    void calculateExponentialBackoff_ShouldAddJitter() {
        // Arrange
        int retryCount = 1;

        // Act - run multiple times to test jitter
        long delay1 = retryUtil.calculateExponentialBackoff(retryCount);
        long delay2 = retryUtil.calculateExponentialBackoff(retryCount);
        long delay3 = retryUtil.calculateExponentialBackoff(retryCount);

        // Assert - values should be different due to jitter
        assertThat(delay1).isNotEqualTo(delay2);
        assertThat(delay2).isNotEqualTo(delay3);
        assertThat(delay1).isNotEqualTo(delay3);
    }
}
