package com.ako.dbuff.service.replay.analysis.phase;

import java.util.ArrayList;
import java.util.List;

/** The phases of a game: laning, mid game and late game, cut at fixed minutes. */
public final class PhaseWindows {
  private PhaseWindows() {}

  /**
   * @param name LANE, MID or LATE
   * @param endSeconds exclusive, except for the last phase, which ends with the game
   */
  public record Window(String name, int startSeconds, int endSeconds) {
    public int length() {
      return endSeconds - startSeconds;
    }
  }

  /** The phases that have any time in a game of {@code gameSeconds}; none for a game of 0. */
  public static List<Window> of(int laneEndMinutes, int midEndMinutes, int gameSeconds) {
    int laneEnd = laneEndMinutes * 60;
    int midEnd = Math.max(laneEnd, midEndMinutes * 60);
    List<Window> windows = new ArrayList<>();
    add(windows, "LANE", 0, Math.min(laneEnd, gameSeconds));
    add(windows, "MID", laneEnd, Math.min(midEnd, gameSeconds));
    add(windows, "LATE", midEnd, gameSeconds);
    return windows;
  }

  private static void add(List<Window> windows, String name, int start, int end) {
    if (end > start) {
      windows.add(new Window(name, start, end));
    }
  }
}
