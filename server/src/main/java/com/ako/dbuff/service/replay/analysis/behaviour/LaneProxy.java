package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Optional;

/**
 * The creep-kill stand-in for how the lane a hero left fared, since wave positions are not stored:
 * the creeps his lane partners killed against those the enemy heroes of the lane killed, in the
 * window after he left.
 */
public final class LaneProxy {
  /**
   * @param own creeps the hero's partners killed in the lane
   * @param enemy creeps the enemy heroes of the lane killed there
   */
  public record Result(int own, int enemy) {
    public boolean lost() {
      return enemy > own;
    }
  }

  private final int windowSeconds;

  public LaneProxy(int windowSeconds) {
    this.windowSeconds = windowSeconds;
  }

  /** Empty when the hero had no lane, or had already left it, at the start of the trip. */
  public Optional<Result> of(
      long slot, int leftSeconds, List<LaneSlot> lanes, List<CreepHit> creeps) {
    Optional<LaneSlot> mine =
        lanes.stream()
            .filter(l -> l.slot() == slot)
            .filter(l -> l.startSeconds() <= leftSeconds && leftSeconds <= l.endSeconds())
            .findFirst();
    if (mine.isEmpty()) {
      return Optional.empty();
    }
    LaneSlot lane = mine.get();
    int own = 0;
    int enemy = 0;
    for (CreepHit hit : creeps) {
      if (hit.denied()
          || hit.lane() != lane.lane()
          || hit.slot() == slot
          || hit.seconds() < leftSeconds
          || hit.seconds() >= leftSeconds + windowSeconds) {
        continue;
      }
      if (TeamSide.ofSlot(hit.slot()) == lane.team()) {
        own++;
      } else {
        enemy++;
      }
    }
    return Optional.of(new Result(own, enemy));
  }
}
