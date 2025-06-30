package com.example.workfloworchestrator.exception;

/**
 * Exception thrown when JSON extraction operations fail.
 * This includes errors in JSON path resolution, invalid path formats,
 * or transformation failures during attribute extraction.
 */
public class JsonExtractionException extends Exception {

  /**
   * Constructs a new JsonExtractionException with the specified detail message.
   *
   * @param message the detail message explaining the cause of the exception
   */
  public JsonExtractionException(String message) {
    super(message);
  }

  /**
   * Constructs a new JsonExtractionException with the specified detail message and cause.
   *
   * @param message the detail message explaining the cause of the exception
   * @param cause the cause of the exception (which is saved for later retrieval)
   */
  public JsonExtractionException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new JsonExtractionException with the specified cause.
   *
   * @param cause the cause of the exception (which is saved for later retrieval)
   */
  public JsonExtractionException(Throwable cause) {
    super(cause);
  }

  /**
   * Constructs a new JsonExtractionException with the specified detail message,
   * cause, suppression enabled or disabled, and writable stack trace enabled or disabled.
   *
   * @param message the detail message
   * @param cause the cause
   * @param enableSuppression whether suppression is enabled or disabled
   * @param writableStackTrace whether the stack trace should be writable
   */
  protected JsonExtractionException(String message, Throwable cause,
                                    boolean enableSuppression,
                                    boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
