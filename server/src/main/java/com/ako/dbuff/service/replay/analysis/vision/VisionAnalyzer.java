package com.ako.dbuff.service.replay.analysis.vision;

import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightVisionRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.dao.repo.WardEventRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one {@code fight_vision} row per team for every important fight: its wards near the fight
 * when it began, the enemy's invisible heroes and its detection, and the verdict. Nothing outside
 * the windows of the important fights is looked at. A match with no ward log, which comes from
 * OpenDota, gets no rows.
 */
@Slf4j
@Order(80)
@Component
@RequiredArgsConstructor
public class VisionAnalyzer implements ReplayAnalyzer {
  private final FightExplanationReader fights;
  private final WardEventRepo wardRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final ReplayPlayerRepo playerRepo;
  private final ReplayStateChangeRepo stateChangeRepo;
  private final CombatLogReader combatLog;
  private final InvisibleHeroes invisibleHeroes;
  private final VisionProperties properties;
  private final FightVisionRepo visionRepo;

  @Override
  public String name() {
    return "vision";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("important-fights", "fights", "combat-log", "state-changes", "players");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<WardEventDomain> wards = wardRepo.findByMatchId(matchId);
    if (wards.isEmpty()) {
      log.info("Match {} has no ward log, so no vision rows", matchId);
      return;
    }
    List<LostFight> important = fights.importantFights(matchId);
    if (important.isEmpty()) {
      return;
    }
    FightVisionAssessor assessor =
        new FightVisionAssessor(facts(matchId, wards), properties, version());
    for (LostFight lost : important) {
      for (TeamSide team : TeamSide.values()) {
        FightVisionDomain row = assessor.assess(lost.fight(), team);
        if (row != null) {
          visionRepo.save(row);
        }
      }
    }
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    visionRepo.deleteByMatchId(matchId);
  }

  private FightVisionAssessor.MatchFacts facts(long matchId, List<WardEventDomain> wards) {
    List<CombatEvent> events = combatLog.events(matchId);
    Map<String, Long> slotByHero = new HashMap<>();
    CombatHeroes.playerIndexByHero(events, playerRepo.findByMatchId(matchId))
        .forEach((hero, index) -> slotByHero.put(hero, PlayerSlots.slot(index)));
    List<ReplayStateChangeDomain> changes =
        stateChangeRepo.findByMatchIdOrderByGameSeconds(matchId);
    return new FightVisionAssessor.MatchFacts(
        matchId,
        wards,
        new InvisibilityLog(events, slotByHero, invisibleHeroes),
        new DetectionLog(events, slotByHero, changes),
        presentSlots(matchId),
        abilities(changes),
        slotByHero);
  }

  private Map<Integer, Set<Long>> presentSlots(long matchId) {
    Map<Integer, Set<Long>> present = new HashMap<>();
    for (FightPlayerDomain p : fightPlayerRepo.findByMatchId(matchId)) {
      Presence presence = Enums.find(Presence.class, p.getPresence());
      if (p.getFightIndex() != null
          && p.getPlayerSlot() != null
          && (presence == Presence.ACTIVE || presence == Presence.PRESENT_PASSIVE)) {
        present.computeIfAbsent(p.getFightIndex(), i -> new HashSet<>()).add(p.getPlayerSlot());
      }
    }
    return present;
  }

  private Map<Long, Map<String, Double>> abilities(List<ReplayStateChangeDomain> changes) {
    Map<Long, Map<String, Double>> owned = new HashMap<>();
    changes.stream()
        .filter(c -> StateChangeKind.ABILITY_LEVEL.name().equals(c.getKind()))
        .filter(c -> c.getPlayerSlot() != null && c.getGameSeconds() != null)
        .filter(c -> c.getAbilityLevel() != null && c.getAbilityLevel() > 0)
        .filter(c -> invisibleHeroes.abilities().containsKey(c.getName()))
        .forEach(
            c ->
                owned
                    .computeIfAbsent(c.getPlayerSlot(), s -> new HashMap<>())
                    .merge(c.getName(), c.getGameSeconds(), Math::min));
    return owned;
  }
}
