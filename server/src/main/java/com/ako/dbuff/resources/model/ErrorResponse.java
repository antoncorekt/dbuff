package com.ako.dbuff.resources.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Standard error response for API errors. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

  /** Error code for programmatic handling */
  private String code;

  /** Human-readable error message */
  private String message;

  /** Additional details about the error */
  private String details;

  public static ErrorResponse of(String code, String message) {
    return ErrorResponse.builder().code(code).message(message).build();
  }

  public static ErrorResponse of(String code, String message, String details) {
    return ErrorResponse.builder().code(code).message(message).details(details).build();
  }
}
