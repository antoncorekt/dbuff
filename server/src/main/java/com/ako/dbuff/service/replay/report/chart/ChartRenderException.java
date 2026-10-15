package com.ako.dbuff.service.replay.report.chart;

/** A chart could not be drawn, for instance because the JVM has no working AWT. */
public class ChartRenderException extends RuntimeException {
  public ChartRenderException(String message, Throwable cause) {
    super(message, cause);
  }
}
