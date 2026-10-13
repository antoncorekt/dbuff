package com.ako.dbuff.service.replay.analysis.behaviour;

/** How a team stood in one stretch of the game. */
public enum Posture {
  GROUPED("together"),
  SPLIT("split up"),
  MIXED("mixed");

  private final String words;

  Posture(String words) {
    this.words = words;
  }

  public String words() {
    return words;
  }
}
