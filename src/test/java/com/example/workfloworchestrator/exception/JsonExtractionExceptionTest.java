package com.example.workfloworchestrator.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JsonExtractionExceptionTest {

  @Test
  void shouldCreateExceptionWithMessage() {
    String errorMessage = "JSON extraction failed";
    
    JsonExtractionException exception = new JsonExtractionException(errorMessage);
    
    assertThat(exception.getMessage()).isEqualTo(errorMessage);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  void shouldCreateExceptionWithMessageAndCause() {
    String errorMessage = "JSON extraction failed";
    RuntimeException cause = new RuntimeException("Root cause");
    
    JsonExtractionException exception = new JsonExtractionException(errorMessage, cause);
    
    assertThat(exception.getMessage()).isEqualTo(errorMessage);
    assertThat(exception.getCause()).isEqualTo(cause);
  }

  @Test
  void shouldCreateExceptionWithCause() {
    RuntimeException cause = new RuntimeException("Root cause");
    
    JsonExtractionException exception = new JsonExtractionException(cause);
    
    assertThat(exception.getMessage()).isEqualTo(cause.toString());
    assertThat(exception.getCause()).isEqualTo(cause);
  }

  @Test
  void shouldCreateExceptionWithAllParameters() {
    String errorMessage = "JSON extraction failed";
    RuntimeException cause = new RuntimeException("Root cause");
    boolean enableSuppression = true;
    boolean writableStackTrace = true;
    
    // Use reflection to access protected constructor
    JsonExtractionException exception = new JsonExtractionException(
        errorMessage, cause, enableSuppression, writableStackTrace);
    
    assertThat(exception.getMessage()).isEqualTo(errorMessage);
    assertThat(exception.getCause()).isEqualTo(cause);
  }

  @Test
  void shouldBeInstanceOfException() {
    JsonExtractionException exception = new JsonExtractionException("test");
    
    assertThat(exception).isInstanceOf(Exception.class);
  }

  @Test
  void shouldHaveCorrectStackTrace() {
    JsonExtractionException exception = new JsonExtractionException("test");
    
    assertThat(exception.getStackTrace()).isNotEmpty();
    assertThat(exception.getStackTrace()[0].getClassName())
        .isEqualTo(JsonExtractionExceptionTest.class.getName());
  }

  @Test
  void shouldSupportSuppressedExceptions() {
    JsonExtractionException mainException = new JsonExtractionException("Main error");
    RuntimeException suppressedException = new RuntimeException("Suppressed error");
    
    mainException.addSuppressed(suppressedException);
    
    assertThat(mainException.getSuppressed()).hasSize(1);
    assertThat(mainException.getSuppressed()[0]).isEqualTo(suppressedException);
  }

  @Test
  void shouldChainProperly() {
    RuntimeException rootCause = new RuntimeException("Root cause");
    JsonExtractionException middleException = new JsonExtractionException("Middle", rootCause);
    JsonExtractionException topException = new JsonExtractionException("Top", middleException);
    
    assertThat(topException.getCause()).isEqualTo(middleException);
    assertThat(topException.getCause().getCause()).isEqualTo(rootCause);
  }

  @Test
  void shouldHandleNullMessage() {
    JsonExtractionException exception = new JsonExtractionException((String) null);
    
    assertThat(exception.getMessage()).isNull();
  }

  @Test
  void shouldHandleNullCause() {
    JsonExtractionException exception = new JsonExtractionException("message", null);
    
    assertThat(exception.getMessage()).isEqualTo("message");
    assertThat(exception.getCause()).isNull();
  }
}
