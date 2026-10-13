package com.ako.dbuff.service.replay.analysis.behaviour;

import java.util.Set;

/**
 * A fight as the rotation rules need it.
 *
 * @param winner RADIANT, DIRE or DRAW
 * @param present the slots that took part (active or close and passive)
 */
public record FightFact(int index, double start, double end, String winner, Set<Long> present) {}
