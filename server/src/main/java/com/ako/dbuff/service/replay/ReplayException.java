package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.ReplayStatus;
import lombok.Getter;

/** A replay problem that maps to a stored {@link ReplayStatus} rather than to a crash. */
@Getter
public class ReplayException extends RuntimeException {
  private final ReplayStatus status;

  public ReplayException(ReplayStatus status, String message) {
    super(message);
    this.status = status;
  }

  public ReplayException(ReplayStatus status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }
}
