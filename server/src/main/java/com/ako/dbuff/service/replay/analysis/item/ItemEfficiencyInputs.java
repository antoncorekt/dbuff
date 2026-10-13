package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.repo.CreepKillRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads a match's stored facts once and sorts them out per hero for the evaluators. */
@Component
@RequiredArgsConstructor
public class ItemEfficiencyInputs {
  private static final Set<CombatEventKind> ABOUT_THE_TARGET =
      Set.of(
          CombatEventKind.DAMAGE, CombatEventKind.DEATH, CombatEventKind.GOLD, CombatEventKind.XP);

  /** The heroes of the match and the last second the stored data reaches. */
  public record MatchInputs(int gameEndSeconds, List<HeroInputs> heroes) {}

  private final ReplayPlayerRepo playerRepo;
  private final ReplayPlayerTrackRepo trackRepo;
  private final ReplayStateChangeRepo stateRepo;
  private final CreepKillRepo creepKillRepo;
  private final StoredActivity activity;
  private final CombatLogReader combatLog;

  public MatchInputs load(long matchId) {
    List<CombatEvent> events = combatLog.events(matchId);
    Set<String> heroNames = CombatHeroes.names(events);
    Map<Long, ReplayPlayerTrackDomain> tracks = new HashMap<>();
    trackRepo.findByMatchId(matchId).forEach(t -> tracks.putIfAbsent(t.getPlayerSlot(), t));
    Map<Long, List<ReplayStateChangeDomain>> changes = new HashMap<>();
    stateRepo
        .findByMatchIdOrderByGameSeconds(matchId)
        .forEach(c -> changes.computeIfAbsent(c.getPlayerSlot(), k -> new ArrayList<>()).add(c));
    Map<Long, List<CreepKillDomain>> kills = new HashMap<>();
    creepKillRepo.findByMatchId(matchId).stream()
        .sorted(Comparator.comparing(CreepKillDomain::getSeconds))
        .forEach(k -> kills.computeIfAbsent(k.getPlayerSlot(), s -> new ArrayList<>()).add(k));
    Map<Long, List<ActivitySegment>> segments = activity.bySlot(matchId);
    List<HeroInputs> heroes = new ArrayList<>();
    for (ReplayPlayerDomain player : playerRepo.findByMatchId(matchId)) {
      String hero = CombatHeroes.nameOf(player, heroNames);
      Long slot = player.getPlayerSlot();
      if (hero != null && slot != null) {
        heroes.add(
            new HeroInputs(
                slot,
                hero,
                changes.getOrDefault(slot, List.of()),
                eventsOf(events, hero),
                tracks.get(slot),
                kills.getOrDefault(slot, List.of()),
                segments.getOrDefault(slot, List.of())));
      }
    }
    return new MatchInputs(gameEnd(events, tracks.values()), heroes);
  }

  private static List<CombatEvent> eventsOf(List<CombatEvent> events, String hero) {
    return events.stream()
        .filter(
            e ->
                e.kind() == CombatEventKind.ITEM
                    ? hero.equals(e.attacker())
                    : ABOUT_THE_TARGET.contains(e.kind()) && hero.equals(e.target()))
        .toList();
  }

  private static int gameEnd(List<CombatEvent> events, Iterable<ReplayPlayerTrackDomain> tracks) {
    double end = events.stream().mapToDouble(CombatEvent::gameSecond).max().orElse(0);
    for (ReplayPlayerTrackDomain t : tracks) {
      if (t.getSampleCount() != null && t.getStepSeconds() != null && t.getStartSeconds() != null) {
        end = Math.max(end, t.getStartSeconds() + (t.getSampleCount() - 1.0) * t.getStepSeconds());
      }
    }
    return (int) Math.ceil(end);
  }
}
