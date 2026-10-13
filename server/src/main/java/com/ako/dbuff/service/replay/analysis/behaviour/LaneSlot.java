package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;

/** A hero's stored lane and the game seconds he laned in it. */
public record LaneSlot(
    long slot, TeamSide team, Zone.Lane lane, int startSeconds, int endSeconds) {}
