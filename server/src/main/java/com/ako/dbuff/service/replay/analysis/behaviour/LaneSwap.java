package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;

/** A hero who stood in another lane than his stored one, from the first to the last time there. */
public record LaneSwap(
    long slot, TeamSide team, Zone.Lane before, Zone.Lane after, int fromSeconds, int toSeconds) {}
