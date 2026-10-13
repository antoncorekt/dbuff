package com.ako.dbuff.service.replay.analysis.behaviour;

import java.util.Optional;

/**
 * Everything the reason rules read about one trip.
 *
 * @param heroHits hits on enemy heroes while he was at the destination
 * @param towerHits hits on towers then
 * @param missedFight a fight in the lane he got to was over before he arrived, and he took part in
 *     none there
 * @param towerLost an own tower fell during the trip
 * @param towerInHisLane one of them was in the lane he left
 */
public record ReasonFacts(
    AwayTrip trip,
    TripOutcome outcome,
    Optional<LaneProxy.Result> proxy,
    Optional<Income> income,
    double incomeShare,
    int heroHits,
    int towerHits,
    boolean missedFight,
    boolean towerLost,
    boolean towerInHisLane) {}
