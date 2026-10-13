package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.Optional;

/**
 * An away trip that ended in another lane and came to something.
 *
 * @param laneLeft the lane he was laning in when he left; null without one
 */
public record Rotation(
    TeamSide team,
    AwayTrip trip,
    Zone.Lane laneLeft,
    TripOutcome outcome,
    Optional<LaneProxy.Result> proxy,
    RotationVerdict verdict) {}
