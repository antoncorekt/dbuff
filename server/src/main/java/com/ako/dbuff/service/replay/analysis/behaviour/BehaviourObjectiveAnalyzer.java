package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameStateSegmentRepo;
import com.ako.dbuff.dao.repo.ObjectiveConversionRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.behaviour.ObjectiveConversion.Nearest;
import com.ako.dbuff.service.replay.analysis.behaviour.ObjectiveConversion.RoshanNote;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.Comparator;
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
 * Stores one {@code objective_conversion} row for each important fight with a winner: whether the
 * winner took a tower, barracks, the ancient or Roshan in the window after it, and if not, the
 * enemy tower nearest to its heroes. Reads the stored importance, fights, building kills, team
 * states, tracks and the combat log.
 */
@Slf4j
@Order(94)
@Component
@RequiredArgsConstructor
public class BehaviourObjectiveAnalyzer implements ReplayAnalyzer {
  private final MatchMapLayout matchMapLayout;
  private final SlotTraces slotTraces;
  private final ReplayPlayerRepo playerRepo;
  private final CombatLogReader combatLog;
  private final FightRepo fightRepo;
  private final FightImportanceRepo importanceRepo;
  private final GameStateSegmentRepo stateRepo;
  private final BuildingKillContextRepo buildingRepo;
  private final BehaviourProperties properties;
  private final ObjectiveConversionRepo conversionRepo;

  @Override
  public String name() {
    return "objectives";
  }

  @Override
  public int version() {
    return 2;
  }

  @Override
  public Set<String> requires() {
    return Set.of(
        "important-fights",
        "fights",
        "building-kills",
        "combat-log",
        "tracks",
        "team-state",
        "players");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    MapLayout layout = matchMapLayout.of(matchId).orElse(null);
    if (layout == null) {
      log.info(
          "Match {} was not played on the frozen map layout, objective conversions left as they"
              + " were",
          matchId);
      return;
    }
    Map<Long, Trace> traces = slotTraces.of(matchId);
    List<CombatEvent> events = combatLog.events(matchId);
    if (traces.isEmpty() || events.isEmpty()) {
      log.info(
          "Match {} has no tracks or combat log, objective conversions left as they were", matchId);
      return;
    }
    List<ObjectiveFight> fights =
        ObjectiveFights.of(
            importanceRepo.findByMatchId(matchId),
            fightRepo.findByMatchId(matchId),
            stateRepo.findByMatchId(matchId));
    Map<String, Long> slotByHero = CombatSlots.byHero(events, playerRepo.findByMatchId(matchId));
    List<TowerFall> buildings = TowerFall.ofObjectives(buildingRepo.findByMatchId(matchId));
    ObjectiveFinder.Found found =
        new ObjectiveFinder(properties, layout::zone)
            .find(
                fights,
                traces,
                new ObjectiveFinder.Events(
                    buildings,
                    SmokeEvents.roshanKills(events, slotByHero),
                    StandingTower.of(layout.facts()),
                    gameEnd(buildings, traces)));
    if (found.cutShort() > 0) {
      log.info(
          "Match {}: {} fights left out, nothing taken and the game ended inside the window",
          matchId,
          found.cutShort());
    }
    deleteByMatchId(matchId);
    conversionRepo.saveAll(found.conversions().stream().map(c -> row(matchId, c)).toList());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    conversionRepo.deleteByMatchId(matchId);
  }

  private ObjectiveConversionDomain row(long matchId, ObjectiveConversion c) {
    Nearest nearest = c.nearest();
    RoshanNote roshan = c.roshan();
    Zone heroZone = nearest == null ? null : nearest.heroZone();
    return ObjectiveConversionDomain.builder()
        .matchId(matchId)
        .fightIndex(c.fightIndex())
        .winnerTeam(c.winner().name())
        .result(c.result().name())
        .objectiveKind(name(c.kind()))
        .objectiveKey(c.key())
        .secondsAfter(c.secondsAfter())
        .stateBefore(name(c.stateBefore()))
        .winnersAlive(c.winnersAlive())
        .nearestTower(nearest == null ? null : nearest.tower().name())
        .towerLane(nearest == null ? null : name(nearest.tower().lane()))
        .towerDistance(nearest == null ? null : (int) Math.round(nearest.distance()))
        .nearestHeroSlot(nearest == null ? null : nearest.heroSlot())
        .heroZone(heroZone == null ? null : name(heroZone.kind()))
        .heroLane(heroZone == null ? null : name(heroZone.lane()))
        .roshanNearSlots(
            roshan == null || roshan.nearSlots() == null
                ? null
                : roshan.nearSlots().stream()
                    .sorted(Comparator.naturalOrder())
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")))
        .roshanCall(roshan == null ? null : name(roshan.call()))
        .windowSeconds(properties.getObjectiveWindowSeconds())
        .analysisVersion(version())
        .build();
  }

  /** The ancient's fall when it fell, else the last track sample of any hero. */
  private static double gameEnd(List<TowerFall> buildings, Map<Long, Trace> traces) {
    return buildings.stream()
        .filter(b -> "FORT".equals(b.kind()))
        .mapToDouble(TowerFall::seconds)
        .min()
        .orElseGet(
            () ->
                traces.values().stream()
                    .mapToDouble(t -> t.startSeconds() + (t.x().length - 1.0) * t.stepSeconds())
                    .max()
                    .orElse(0));
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }
}
