package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import com.ako.dbuff.dao.repo.ReplayPlayerMinuteRepo;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores each player's cumulative counters once a game minute. */
@Component
@RequiredArgsConstructor
public class ReplayMinuteExtractor implements ReplayExtractor {
  private final ReplayPlayerMinuteRepo repo;

  @Override
  public String name() {
    return "minutes";
  }

  @Override
  @Transactional
  public void extract(ReplayFacts facts, MatchDomain match) {
    deleteByMatchId(match.getId());
    SampleFacts samples = facts.samples();
    if (samples == null) {
      return;
    }
    Map<Integer, ReplayPlayerFact> players = facts.playersById();
    List<ReplayPlayerMinuteDomain> rows =
        samples.minutes().stream()
            .filter(snapshot -> players.containsKey(snapshot.replayPlayerId()))
            .map(snapshot -> row(match.getId(), players.get(snapshot.replayPlayerId()), snapshot))
            .toList();
    repo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }

  private static ReplayPlayerMinuteDomain row(
      long matchId, ReplayPlayerFact player, SampleFacts.MinuteSnapshot snapshot) {
    long[] values = snapshot.values();
    return ReplayPlayerMinuteDomain.builder()
        .matchId(matchId)
        .playerSlot(player.playerSlot())
        .gameMinute(snapshot.gameMinute())
        .heroKillGold(value(values, MinuteStat.HERO_KILL_GOLD))
        .creepKillGold(value(values, MinuteStat.CREEP_KILL_GOLD))
        .neutralKillGold(value(values, MinuteStat.NEUTRAL_KILL_GOLD))
        .bountyGold(value(values, MinuteStat.BOUNTY_GOLD))
        .roshanGold(value(values, MinuteStat.ROSHAN_GOLD))
        .buildingGold(value(values, MinuteStat.BUILDING_GOLD))
        .courierGold(value(values, MinuteStat.COURIER_GOLD))
        .comebackGold(value(values, MinuteStat.COMEBACK_GOLD))
        .incomeGold(value(values, MinuteStat.INCOME_GOLD))
        .sharedGold(value(values, MinuteStat.SHARED_GOLD))
        .wardKillGold(value(values, MinuteStat.WARD_KILL_GOLD))
        .creepDenyGold(value(values, MinuteStat.CREEP_DENY_GOLD))
        .otherGold(value(values, MinuteStat.OTHER_GOLD))
        .reliableGold(value(values, MinuteStat.RELIABLE_GOLD))
        .unreliableGold(value(values, MinuteStat.UNRELIABLE_GOLD))
        .nearbyCreepDeaths(value(values, MinuteStat.NEARBY_CREEP_DEATHS))
        .missCount(value(values, MinuteStat.MISS_COUNT))
        .claimedMissCount(value(values, MinuteStat.CLAIMED_MISS_COUNT))
        .obsPlaced(value(values, MinuteStat.OBS_PLACED))
        .sentPlaced(value(values, MinuteStat.SENT_PLACED))
        .wardsDestroyed(value(values, MinuteStat.WARDS_DESTROYED))
        .creepsStacked(value(values, MinuteStat.CREEPS_STACKED))
        .campsStacked(value(values, MinuteStat.CAMPS_STACKED))
        .goldLostToDeath(value(values, MinuteStat.GOLD_LOST_TO_DEATH))
        .goldSpentOnBuybacks(value(values, MinuteStat.GOLD_SPENT_ON_BUYBACKS))
        .courierKills(value(values, MinuteStat.COURIER_KILLS))
        .kills(value(values, MinuteStat.KILLS))
        .deaths(value(values, MinuteStat.DEATHS))
        .assists(value(values, MinuteStat.ASSISTS))
        .bountyRunes(value(values, MinuteStat.BOUNTY_RUNES))
        .powerRunes(value(values, MinuteStat.POWER_RUNES))
        .waterRunes(value(values, MinuteStat.WATER_RUNES))
        .build();
  }

  private static Long value(long[] values, MinuteStat stat) {
    long value = values[stat.ordinal()];
    return value == MinuteStat.MISSING ? null : value;
  }
}
