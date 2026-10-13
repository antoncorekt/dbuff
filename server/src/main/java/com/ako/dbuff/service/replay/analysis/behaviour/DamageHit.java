package com.ako.dbuff.service.replay.analysis.behaviour;

/**
 * One hit a hero dealt, either to an enemy hero or to a tower.
 *
 * @param tower the target was a tower, else a hero
 */
public record DamageHit(long slot, double seconds, boolean tower) {}
