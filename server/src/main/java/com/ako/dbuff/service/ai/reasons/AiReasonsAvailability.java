package com.ako.dbuff.service.ai.reasons;

/** Whether the AI reasons can be asked for at all, before any match is looked at. */
public enum AiReasonsAvailability {
  AVAILABLE(""),
  SWITCHED_OFF("the AI reasons are switched off on this server (replay.report.ai.enabled)"),
  NO_API_KEY("no OpenAI API key is configured");

  private final String reason;

  AiReasonsAvailability(String reason) {
    this.reason = reason;
  }

  public String reason() {
    return reason;
  }
}
