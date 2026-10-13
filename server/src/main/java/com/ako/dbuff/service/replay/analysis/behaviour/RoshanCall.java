package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter.State;

/** How a Roshan kill after a won fight reads, from where the winner stood before the fight. */
public enum RoshanCall {
  EXPECTED("expected, the winner was ahead"),
  GAMBLE("a gamble, the winner was behind"),
  EVEN("the winner was about even");

  private final String words;

  RoshanCall(String words) {
    this.words = words;
  }

  public String words() {
    return words;
  }

  /** Null when the state before the fight is not known. */
  static RoshanCall of(State stateBefore) {
    if (stateBefore == null) {
      return null;
    }
    return switch (stateBefore) {
      case AHEAD -> EXPECTED;
      case BEHIND -> GAMBLE;
      case EVEN -> EVEN;
    };
  }
}
