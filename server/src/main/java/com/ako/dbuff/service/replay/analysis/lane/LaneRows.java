package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.LanePlayerStatDomain;
import com.ako.dbuff.dao.model.LaneReasonDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import java.util.List;

/** The rows the lane analysis of one match stores. */
record LaneRows(
    List<LaneAssignmentDomain> assignments,
    List<LanePlayerStatDomain> playerStats,
    List<LaneResultDomain> results,
    List<LaneReasonDomain> reasons) {}
