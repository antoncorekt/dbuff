package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter.State;

/**
 * An important fight with a winner.
 *
 * @param stateBefore the winner's net worth state at the fight's start, null when not known
 */
public record ObjectiveFight(
    int index, TeamSide winner, double startSeconds, double endSeconds, State stateBefore) {}
