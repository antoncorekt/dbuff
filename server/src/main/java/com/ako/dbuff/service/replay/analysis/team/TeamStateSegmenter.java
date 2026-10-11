package com.ako.dbuff.service.replay.analysis.team;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoublePredicate;

/**
 * Splits a game into stretches in which a team was ahead, behind or about even, from how far its
 * net worth led the other team's at each sample. A state is entered only after the lead has held
 * for a while and left only after it has faded for a while, so a lead that wobbles around the
 * threshold does not flap.
 */
public final class TeamStateSegmenter {

  public enum State {
    AHEAD,
    EVEN,
    BEHIND
  }

  /**
   * @param aheadFraction the lead, as a share of both teams' net worth together, from which a team
   *     is ahead; it stays ahead until the lead has fallen below half of it
   * @param enterSeconds how long the lead must hold before the state is entered
   * @param leaveSeconds how long it must have faded before the state is left
   */
  public record Tuning(double aheadFraction, int enterSeconds, int leaveSeconds) {}

  /**
   * @param endSeconds where the next segment starts, or the last sample for the final one
   * @param peakLead the lead furthest from zero in the segment, positive when ahead
   */
  public record Segment(State state, int startSeconds, int endSeconds, double peakLead) {}

  private final Tuning tuning;

  public TeamStateSegmenter(Tuning tuning) {
    this.tuning = tuning;
  }

  /**
   * @param leads the lead of the team at each sample, positive when it is ahead
   * @param stepSeconds the time between samples; sample {@code i} is at {@code i * stepSeconds}
   */
  public List<Segment> segment(double[] leads, int stepSeconds) {
    int enterCount = Math.max(1, (int) Math.ceil(tuning.enterSeconds() / (double) stepSeconds));
    int leaveCount = Math.max(1, (int) Math.ceil(tuning.leaveSeconds() / (double) stepSeconds));
    List<Segment> segments = new ArrayList<>();
    State state = State.EVEN;
    int start = 0;
    for (int i = 0; i < leads.length; i++) {
      State next = next(state, leads, start, i, enterCount, leaveCount);
      if (next != state) {
        int switchAt = next == State.EVEN ? i - leaveCount + 1 : i - enterCount + 1;
        if (switchAt > start) {
          segments.add(segment(state, leads, start, switchAt, switchAt - 1, stepSeconds));
        }
        state = next;
        start = Math.max(start, switchAt);
      }
    }
    if (leads.length > 0) {
      segments.add(segment(state, leads, start, leads.length - 1, leads.length - 1, stepSeconds));
    }
    return segments;
  }

  private State next(
      State state, double[] leads, int start, int i, int enterCount, int leaveCount) {
    double a = tuning.aheadFraction();
    return switch (state) {
      case EVEN ->
          held(leads, start, i, enterCount, l -> l >= a)
              ? State.AHEAD
              : held(leads, start, i, enterCount, l -> l <= -a) ? State.BEHIND : State.EVEN;
      case AHEAD -> held(leads, start, i, leaveCount, l -> l < a / 2) ? State.EVEN : State.AHEAD;
      case BEHIND -> held(leads, start, i, leaveCount, l -> l > -a / 2) ? State.EVEN : State.BEHIND;
    };
  }

  /**
   * Whether the last {@code count} samples up to {@code i}, none before {@code start}, all hold.
   */
  private static boolean held(double[] leads, int start, int i, int count, DoublePredicate test) {
    if (i - count + 1 < start) {
      return false;
    }
    for (int j = i - count + 1; j <= i; j++) {
      if (!test.test(leads[j])) {
        return false;
      }
    }
    return true;
  }

  private static Segment segment(
      State state, double[] leads, int from, int to, int lastSample, int step) {
    double peak = 0;
    for (int j = from; j <= lastSample; j++) {
      if (Math.abs(leads[j]) > Math.abs(peak)) {
        peak = leads[j];
      }
    }
    return new Segment(state, from * step, to * step, peak);
  }
}
