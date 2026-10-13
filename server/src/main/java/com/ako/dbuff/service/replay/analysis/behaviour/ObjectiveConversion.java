package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter.State;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;

/**
 * What the winner of an important fight did with it.
 *
 * @param kind what was taken, null when MISSED
 * @param key the building's combat log name, or the Roshan name, null when MISSED
 * @param secondsAfter whole seconds from the fight's end to the objective, 0 when it fell inside
 *     the fight, null when MISSED
 * @param winnersAlive how many heroes of the winner were alive at the fight's end
 * @param nearest the enemy tower still standing closest to a living hero of the winner at the
 *     fight's end; null when none stands or no hero of the winner was alive
 * @param roshan set when Roshan was the objective
 */
public record ObjectiveConversion(
    int fightIndex,
    TeamSide winner,
    ObjectiveResult result,
    ObjectiveKind kind,
    String key,
    Integer secondsAfter,
    State stateBefore,
    int winnersAlive,
    Nearest nearest,
    RoshanNote roshan) {

  /**
   * @param distance map units from the tower to the winner's closest living hero
   * @param heroZone where that hero stood
   */
  public record Nearest(StandingTower tower, double distance, long heroSlot, Zone heroZone) {}

  /**
   * @param nearSlots the heroes of the winner within the Roshan radius of the hero who killed him
   *     (the killer included when he had a track sample)
   */
  public record RoshanNote(List<Long> nearSlots, RoshanCall call) {}
}
