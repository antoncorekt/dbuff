package com.ako.dbuff.service.replay.analysis.behaviour;

import java.util.List;

/** When, in game seconds, things happened to one team and to its enemy's heroes and towers. */
public record TeamEvents(
    List<Double> towersLost,
    List<Double> towersTaken,
    List<Double> heroesLost,
    List<Double> kills) {}
