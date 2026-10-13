package com.ako.dbuff.service.replay.analysis.rune;

import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.dao.repo.OpeningSummaryRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.RuneEventRepo;
import com.ako.dbuff.dao.repo.RunePickupRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores every rune a hero picked up ({@code rune_pickup}: where, the bounty gold, mid lane or not)
 * and how each team did in the opening window around the first rune spawn ({@code
 * opening_summary}). The opening needs the gold and experience reasons of a version 2 combat log,
 * so an older blob gets pickups without gold and no summary, and a match whose heroes the log does
 * not name gets no summary.
 */
@Slf4j
@Order(14)
@Component
@RequiredArgsConstructor
public class RunePickupAnalyzer implements ReplayAnalyzer {
  private static final int REASONS_FROM_VERSION = 2;

  private final RuneProperties properties;
  private final RuneEventRepo runeRepo;
  private final RunePickupRepo pickupRepo;
  private final OpeningSummaryRepo summaryRepo;
  private final ReplayPlayerRepo playerRepo;
  private final ReplayPlayerTrackRepo trackRepo;
  private final StoredActivity activity;
  private final CombatLogReader combatLog;
  private final MatchMapLayout matchMapLayout;

  @Override
  public String name() {
    return "runes";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "tracks", "activity", "combat-log");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    boolean reasons =
        combatLog.codecVersion(matchId).orElse(CombatLogCodec.VERSION) >= REASONS_FROM_VERSION;
    if (!reasons) {
      log.info(
          "opening summary skipped: combat log stored without gold reasons, re-parse the replay");
    }
    deleteByMatchId(matchId);
    List<CombatEvent> events = combatLog.events(matchId);
    List<ReplayPlayerDomain> roster = playerRepo.findByMatchId(matchId);
    Set<String> heroNames = CombatHeroes.names(events);
    Map<Long, String> heroOfSlot = new HashMap<>();
    Map<String, TeamSide> teamOfHero = new HashMap<>();
    for (ReplayPlayerDomain player : roster) {
      String hero = CombatHeroes.nameOf(player, heroNames);
      if (hero != null && player.getPlayerSlot() != null) {
        heroOfSlot.put(player.getPlayerSlot(), hero);
        teamOfHero.put(hero, TeamSide.ofSlot(player.getPlayerSlot()));
      }
    }
    Map<Long, ReplayPlayerTrackDomain> tracks =
        trackRepo.findByMatchId(matchId).stream()
            .collect(
                Collectors.toMap(
                    ReplayPlayerTrackDomain::getPlayerSlot, Function.identity(), (a, b) -> a));
    List<RunePickupDomain> pickups =
        new RunePickups(
                properties,
                heroOfSlot,
                tracks,
                activity.bySlot(matchId),
                matchMapLayout.of(matchId).orElse(null),
                events)
            .build(matchId, runeRepo.findByMatchId(matchId), version());
    pickupRepo.saveAll(pickups);
    if (reasons && !events.isEmpty() && !teamOfHero.isEmpty()) {
      summaryRepo.saveAll(
          new OpeningSummaries(properties, teamOfHero).build(matchId, pickups, events, version()));
    }
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    pickupRepo.deleteByMatchId(matchId);
    summaryRepo.deleteByMatchId(matchId);
  }
}
