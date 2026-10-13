package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.map.Zone;
import java.util.Set;

/**
 * A fight as the rotation rules need it.
 *
 * @param winner RADIANT, DIRE or DRAW
 * @param present the slots that took part (active or close and passive)
 * @param lane the lane the fight was in; null when it was not in one
 */
public record FightFact(
    int index, double start, double end, String winner, Set<Long> present, Zone.Lane lane) {
  public FightFact(int index, double start, double end, String winner, Set<Long> present) {
    this(index, start, end, winner, present, null);
  }
}
