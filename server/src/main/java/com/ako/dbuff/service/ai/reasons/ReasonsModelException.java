package com.ako.dbuff.service.ai.reasons;

/** A model call that did not produce an answer. */
public class ReasonsModelException extends RuntimeException {
  public ReasonsModelException(String message, Throwable cause) {
    super(message, cause);
  }
}
