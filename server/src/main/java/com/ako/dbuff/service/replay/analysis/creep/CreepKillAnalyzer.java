package com.ako.dbuff.service.replay.analysis.creep;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.repo.CreepKillRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores every lane and camp creep a hero killed, with its class, side, deny flag, the gold and
 * experience received and, when the map layout is known, the zone it happened in. Reads only the
 * stored combat log and roster.
 */
@Slf4j
@Order(12)
@Component
@RequiredArgsConstructor
public class CreepKillAnalyzer implements ReplayAnalyzer {
  private static final int RADIANT_TEAM = 2;
  private static final int REASONS_FROM_VERSION = 2;

  private final CreepKillRepo repo;
  private final ReplayPlayerRepo playerRepo;
  private final CombatLogReader combatLog;
  private final MatchMapLayout matchMapLayout;

  @Override
  public String name() {
    return "creep-kills";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "combat-log");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    if (combatLog.codecVersion(matchId).orElse(CombatLogCodec.VERSION) < REASONS_FROM_VERSION) {
      log.info("creep kills skipped: combat log stored without gold reasons, re-parse the replay");
      return;
    }
    deleteByMatchId(matchId);
    List<CombatEvent> events = combatLog.events(matchId);
    if (events.isEmpty()) {
      return;
    }
    Set<String> heroNames = CombatHeroes.names(events);
    Map<String, ReplayPlayerDomain> players = new HashMap<>();
    Map<String, Zone.Half> halves = new HashMap<>();
    for (ReplayPlayerDomain player : playerRepo.findByMatchId(matchId)) {
      String hero = CombatHeroes.nameOf(player, heroNames);
      if (hero != null) {
        players.putIfAbsent(hero, player);
        halves.putIfAbsent(
            hero, player.getTeamNum() == RADIANT_TEAM ? Zone.Half.RADIANT : Zone.Half.DIRE);
      }
    }
    MapLayout layout = matchMapLayout.of(matchId).orElse(null);
    repo.saveAll(
        CreepKillLinker.link(events, halves).stream()
            .map(kill -> row(matchId, kill, players.get(kill.hero()), layout))
            .toList());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }

  private CreepKillDomain row(
      long matchId, CreepKill kill, ReplayPlayerDomain player, MapLayout layout) {
    Zone zone = kill.x() == null || layout == null ? null : layout.zone(kill.x(), kill.y());
    return CreepKillDomain.builder()
        .matchId(matchId)
        .playerSlot(player.getPlayerSlot())
        .seconds(kill.second())
        .creepClass(kill.creepClass().name())
        .creepName(kill.creepName())
        .side(kill.side() == null ? null : kill.side().name())
        .denied(kill.denied())
        .gold(kill.gold())
        .bonusGold(kill.bonusGold())
        .xp(kill.xp())
        .xpRecipients(kill.xpRecipients())
        .zone(zone == null ? null : zone.kind().name())
        .lane(zone == null || zone.lane() == null ? null : zone.lane().name())
        .camp(zone == null || zone.camp() == Zone.NO_CAMP ? null : zone.camp())
        .analysisVersion(version())
        .build();
  }
}
