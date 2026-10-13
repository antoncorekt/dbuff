package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.map.Zone;

/**
 * One creep a hero killed; {@code lane} is set for a lane creep only.
 *
 * @param denied the killer's own side's creep
 */
public record CreepHit(long slot, double seconds, Zone.Lane lane, boolean denied) {}
