package com.ako.dbuff.service.replay.analysis.lane;

/**
 * One thing that may explain how a side did in its lane, seen from that side.
 *
 * @param code what happened, such as {@code PARTNER_ABSENT}
 * @param direction whether it helped or hurt the side
 * @param subjectSlot the hero it is about; null for a reason about the side
 * @param severity 0 (hardly matters) to 1 (explains the lane); a hypothesis
 * @param evidence the facts behind it, in words
 */
public record LaneReason(
    String code, Direction direction, Long subjectSlot, double severity, String evidence) {

  /** The code of the reason that one side had the ultimate level at lane end and the other not. */
  public static final String NO_ULTIMATE_AT_LANE_END = "NO_ULTIMATE_AT_LANE_END";

  public enum Direction {
    HELPED,
    HURT
  }

  public LaneReason {
    severity = Math.max(0, Math.min(1, severity));
  }

  public static LaneReason hurt(String code, Long subject, double severity, String evidence) {
    return new LaneReason(code, Direction.HURT, subject, severity, evidence);
  }

  public static LaneReason helped(String code, Long subject, double severity, String evidence) {
    return new LaneReason(code, Direction.HELPED, subject, severity, evidence);
  }
}
