package com.ako.dbuff.service.ai.reasons;

/** One plain-English line about the match with its numbers, cited by the model through its id. */
public record Fact(String id, FactKind kind, String text) {

  public int weight() {
    return kind.weight();
  }

  /** The line as the model reads it. */
  public String line() {
    return id + ": " + text;
  }
}
