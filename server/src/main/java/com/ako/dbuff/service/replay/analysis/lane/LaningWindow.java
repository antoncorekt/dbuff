package com.ako.dbuff.service.replay.analysis.lane;

/** The stretch of game time one hero was laning: {@code [startSeconds, endSeconds)}. */
public record LaningWindow(int startSeconds, int endSeconds, LaneEnd end) {

  public boolean contains(double second) {
    return second >= startSeconds && second < endSeconds;
  }

  public int length() {
    return endSeconds - startSeconds;
  }
}
