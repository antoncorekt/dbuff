package com.ako.dbuff.service.replay.analysis.behaviour;

/** Whether the winner of a fight took an objective in the window after it. */
public enum ObjectiveResult {
  CONVERTED("converted"),
  MISSED("missed");

  private final String words;

  ObjectiveResult(String words) {
    this.words = words;
  }

  public String words() {
    return words;
  }
}
