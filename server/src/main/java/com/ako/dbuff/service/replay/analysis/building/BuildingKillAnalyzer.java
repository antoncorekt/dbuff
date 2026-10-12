package com.ako.dbuff.service.replay.analysis.building;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.ObjectiveEventDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ObjectiveEventRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor;
import com.ako.dbuff.service.replay.analysis.fight.FightDetector;
import com.ako.dbuff.service.replay.analysis.fight.FightLookup;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts each fallen tower, barracks and ancient in context: whose and what it was, who took it,
 * which attackers were close and which fight it came in. Runs after the fights it points to.
 */
@Order(45)
@Component
@RequiredArgsConstructor
public class BuildingKillAnalyzer implements ReplayAnalyzer {
  private static final double NEAR_UNITS = 1500;
  private static final double LOOKBACK_SECONDS = 10;
  private static final double KILLER_MATCH_SECONDS = 3;

  private final BuildingKillContextRepo contextRepo;
  private final ObjectiveEventRepo objectiveRepo;
  private final FightRepo fightRepo;
  private final CombatLogReader combatLog;
  private final PlayerTraces playerTraces;
  private final MatchMapLayout matchMapLayout;

  @Override
  public String name() {
    return "building-kills";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "tracks", "combat-log");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<CombatEvent> kills =
        combatLog.events(matchId).stream()
            .filter(e -> e.kind() == CombatEventKind.BUILDING_KILL)
            .toList();
    if (kills.isEmpty()) {
      return;
    }
    List<ObjectiveEventDomain> objectives = new ArrayList<>(objectiveRepo.findByMatchId(matchId));
    List<FightDomain> fights = fightRepo.findByMatchId(matchId);
    var layout = matchMapLayout.of(matchId);
    Map<Integer, FightAssessor.Trace> traces =
        layout.isPresent() ? playerTraces.load(matchId) : Map.of();
    List<BuildingKillContextDomain> rows = new ArrayList<>();
    for (CombatEvent kill : kills) {
      Optional<BuildingName> building = BuildingName.parse(kill.target());
      if (building.isEmpty()) {
        continue;
      }
      ObjectiveEventDomain objective = takeObjective(objectives, kill);
      Long killerSlot = objective == null ? null : objective.getPlayerSlot();
      Optional<double[]> position = layout.flatMap(l -> l.buildingPosition(kill.target()));
      List<Long> near =
          position
              .map(p -> attackersNear(traces, building.get(), p, kill.gameSecond()))
              .orElse(List.of());
      FightDomain fight = FightLookup.at(fights, kill.gameSecond());
      rows.add(
          BuildingKillContextDomain.builder()
              .matchId(matchId)
              .buildingName(kill.target())
              .seconds(kill.gameSecond())
              .kind(building.get().kind())
              .tier(building.get().tier())
              .lane(building.get().lane())
              .ownerTeam(building.get().team())
              .killerSlot(killerSlot)
              .denied(denied(building.get(), objective))
              .attackersNear(position.isPresent() ? near.size() : null)
              .attackerSlots(position.isPresent() ? joined(near) : null)
              .fightIndex(fight == null ? null : fight.getFightIndex())
              .analysisVersion(version())
              .build());
    }
    contextRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    contextRepo.deleteByMatchId(matchId);
  }

  /** The objective event of the same building closest in time, taken so no kill uses it twice. */
  private static ObjectiveEventDomain takeObjective(
      List<ObjectiveEventDomain> objectives, CombatEvent kill) {
    ObjectiveEventDomain best = null;
    for (ObjectiveEventDomain o : objectives) {
      if (kill.target().equals(o.getObjectiveKey())
          && o.getEventTime() != null
          && Math.abs(o.getEventTime() - kill.gameSecond()) <= KILLER_MATCH_SECONDS
          && (best == null
              || Math.abs(o.getEventTime() - kill.gameSecond())
                  < Math.abs(best.getEventTime() - kill.gameSecond()))) {
        best = o;
      }
    }
    objectives.remove(best);
    return best;
  }

  private static Boolean denied(BuildingName building, ObjectiveEventDomain objective) {
    if (objective == null) {
      return null;
    }
    String killerTeam = killerTeam(objective);
    return killerTeam == null ? null : killerTeam.equals(building.team());
  }

  private static String killerTeam(ObjectiveEventDomain objective) {
    if (objective.getPlayerSlot() != null) {
      return objective.getPlayerSlot() >= 128 ? "DIRE" : "RADIANT";
    }
    String unit = String.valueOf(objective.getUnit());
    if (unit.contains("goodguys")) {
      return "RADIANT";
    }
    return unit.contains("badguys") ? "DIRE" : null;
  }

  static List<Long> attackersNear(
      Map<Integer, FightAssessor.Trace> traces, BuildingName building, double[] at, double second) {
    int attackingTeam = building.team().equals("RADIANT") ? 1 : 0;
    List<Long> slots = new ArrayList<>();
    for (Map.Entry<Integer, FightAssessor.Trace> e : traces.entrySet()) {
      if (FightDetector.team(e.getKey()) != attackingTeam) {
        continue;
      }
      FightAssessor.Trace t = e.getValue();
      for (int i = 0; i < t.x().length; i++) {
        double sampledAt = t.startSeconds() + (double) i * t.stepSeconds();
        if (sampledAt >= second - LOOKBACK_SECONDS
            && sampledAt <= second
            && t.level()[i] > 0
            && (t.flags()[i] & 1) == 1
            && Math.hypot(t.x()[i] - at[0], t.y()[i] - at[1]) <= NEAR_UNITS) {
          slots.add(PlayerSlots.slot(e.getKey()));
          break;
        }
      }
    }
    return slots.stream().sorted().toList();
  }

  private static String joined(List<Long> slots) {
    return String.join(",", slots.stream().map(String::valueOf).toList());
  }
}
