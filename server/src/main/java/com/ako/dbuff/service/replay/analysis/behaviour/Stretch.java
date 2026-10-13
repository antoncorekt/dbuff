package com.ako.dbuff.service.replay.analysis.behaviour;

/** A span of whole minutes in which a team kept one posture; the end is exclusive. */
public record Stretch(int startSeconds, int endSeconds, Posture posture) {
  public boolean contains(double seconds) {
    return seconds >= startSeconds && seconds < endSeconds;
  }
}
