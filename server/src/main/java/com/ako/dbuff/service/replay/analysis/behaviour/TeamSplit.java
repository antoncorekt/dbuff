package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;

/** A stretch of a team's posture and what it cost and won: towers, heroes and enemy heroes. */
public record TeamSplit(
    TeamSide team,
    Stretch stretch,
    int towersLost,
    int towersTaken,
    int ownHeroesDied,
    int enemyHeroesDied) {}
