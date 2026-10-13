package com.ako.dbuff.service.replay.analysis.lane;

import java.util.List;

/**
 * Finds reasons for how one side did in its lane, from that side's point of view. Every contributor
 * is common to all game modes; a mode with reasons of its own adds a bean and {@link
 * ReplayLaneAnalyzer} does not change.
 */
interface LaneReasonContributor {
  List<LaneReason> contribute(LaneContext context);
}
