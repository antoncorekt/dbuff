package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** What the contributors read about the whole match; heroes are told apart by player slot. */
record LaneFacts(
    LaneProperties properties,
    Map<Long, String> heroNames,
    Map<Long, Integer> roles,
    Map<Long, HeroConstant> kits,
    Map<Long, LaneHero> laneHeroes,
    Set<Long> withoutLane,
    Map<Long, HeroLaneStats> stats,
    DamageLedger damage,
    List<LaneDeath> deaths,
    List<RunePickupDomain> runes) {

  String name(long slot) {
    return heroNames.getOrDefault(slot, "slot " + slot);
  }

  /** The heroes of a team who are not in the pair's lane: other lanes, or no lane at all. */
  List<Long> outsiders(TeamSide team, Zone.Lane lane) {
    return Stream.concat(
            laneHeroes.values().stream().filter(h -> h.lane() != lane).map(LaneHero::slot),
            withoutLane.stream())
        .filter(slot -> TeamSide.ofSlot(slot) == team)
        .sorted()
        .toList();
  }

  boolean isSupport(long slot) {
    Integer role = roles.get(slot);
    return role != null && role >= 4;
  }
}
