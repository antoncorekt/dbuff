package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.SmokeUseDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.SmokeUseRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one {@code smoke_use} row for each use of a Smoke of Deceit by a hero: who was in the
 * group, where it stood at the start and the end, how it ended (ran out or broke) and what came of
 * it. Reads tracks, the combat log, deaths and building kills.
 */
@Slf4j
@Order(93)
@Component
@RequiredArgsConstructor
public class BehaviourSmokeAnalyzer implements ReplayAnalyzer {
  private final MatchMapLayout matchMapLayout;
  private final SlotTraces slotTraces;
  private final ReplayPlayerRepo playerRepo;
  private final CombatLogReader combatLog;
  private final StoredEventTimes eventTimes;
  private final BuildingKillContextRepo buildingRepo;
  private final BehaviourProperties properties;
  private final SmokeUseRepo smokeRepo;

  @Override
  public String name() {
    return "smoke";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "tracks", "combat-log", "deaths", "building-kills");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    MapLayout layout = matchMapLayout.of(matchId).orElse(null);
    if (layout == null) {
      log.info(
          "Match {} was not played on the frozen map layout, smoke uses left as they were",
          matchId);
      return;
    }
    Map<Long, Trace> traces = slotTraces.of(matchId);
    if (traces.isEmpty()) {
      log.info("Match {} has no tracks, smoke uses left as they were", matchId);
      return;
    }
    List<CombatEvent> events = combatLog.events(matchId);
    Map<String, Long> slotByHero = CombatSlots.byHero(events, playerRepo.findByMatchId(matchId));
    SmokeFinder.Found found =
        new SmokeFinder(properties, layout::zone)
            .find(
                traces,
                SmokeEvents.uses(events, slotByHero),
                SmokeEvents.holds(events, slotByHero),
                new SmokeOutcomes.Events(
                    CombatSlots.killEvents(events, slotByHero),
                    eventTimes.deaths(matchId),
                    TowerFall.ofObjectives(buildingRepo.findByMatchId(matchId)),
                    SmokeEvents.roshanKills(events, slotByHero)));
    if (found.dropped() > 0) {
      log.info(
          "Match {}: {} smoke uses dropped, no modifier logged for the user",
          matchId,
          found.dropped());
    }
    deleteByMatchId(matchId);
    smokeRepo.saveAll(found.uses().stream().map(u -> row(matchId, u)).toList());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    smokeRepo.deleteByMatchId(matchId);
  }

  private SmokeUseDomain row(long matchId, SmokeUse u) {
    return SmokeUseDomain.builder()
        .matchId(matchId)
        .team(u.team().name())
        .userSlot(u.userSlot())
        .groupSlots(u.group().stream().map(String::valueOf).collect(Collectors.joining(",")))
        .startSeconds(u.startSeconds())
        .endSeconds(u.endSeconds())
        .endReason(u.end().name())
        .startZone(zoneKind(u.startZone()))
        .startLane(laneName(u.startZone()))
        .endZone(zoneKind(u.endZone()))
        .endLane(laneName(u.endZone()))
        .outcome(u.result().outcome().name())
        .kills(u.result().kills())
        .buildings(u.result().buildings())
        .roshanKills(u.result().roshanKills())
        .groupDeaths(u.result().groupDeaths())
        .enemyDeaths(u.result().enemyDeaths())
        .analysisVersion(version())
        .build();
  }

  private static String zoneKind(Zone zone) {
    return zone == null ? null : zone.kind().name();
  }

  private static String laneName(Zone zone) {
    return zone == null || zone.lane() == null ? null : zone.lane().name();
  }
}
