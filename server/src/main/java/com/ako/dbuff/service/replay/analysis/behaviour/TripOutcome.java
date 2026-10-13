package com.ako.dbuff.service.replay.analysis.behaviour;

/**
 * What came of a trip.
 *
 * @param kills enemy heroes the hero killed or helped to kill
 * @param creepKills creeps of any kind he killed
 * @param fights fights he took part in
 * @param fightsWon those his team won
 * @param fightIndex the first won fight, else the first fight; null without one
 */
public record TripOutcome(
    int kills, int creepKills, int fights, int fightsWon, Integer fightIndex) {}
