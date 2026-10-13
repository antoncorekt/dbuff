package com.ako.dbuff.service.replay.analysis.behaviour;

/**
 * The count of what happened in a smoke's window, and the outcome they add up to.
 *
 * @param kills enemy hero deaths the group killed or assisted in
 * @param buildings enemy towers, barracks or ancients that fell (a deny is not counted)
 * @param roshanKills Roshan kills by the team
 * @param groupDeaths deaths of heroes of the group
 * @param enemyDeaths deaths of enemy heroes, whoever killed them
 */
public record SmokeResult(
    SmokeOutcome outcome,
    int kills,
    int buildings,
    int roshanKills,
    int groupDeaths,
    int enemyDeaths) {}
