package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Map;

/** Everything the lane rules read about one match, as the earlier analyzers stored it. */
record LaneInputs(
    long matchId,
    Map<Long, TeamSide> teams,
    Map<Long, List<ActivitySegment>> segments,
    List<BuildingKillContextDomain> buildings,
    List<CreepKillDomain> creepKills,
    Map<Long, ReplayPlayerTrackDomain> tracks,
    Map<Long, MinuteSeries> xp,
    Map<Long, MinuteSeries> gold,
    Map<Long, MinuteSeries> stacks,
    List<CombatEvent> events,
    Map<String, Long> slotByHero,
    List<DeathAnalysisDomain> deaths,
    List<RunePickupDomain> runes,
    Map<Long, String> heroNames,
    Map<Long, Integer> roles,
    Map<Long, HeroConstant> kits) {}
