package com.ako.dbuff.service.replay.analysis.death;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimelines;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MapLayouts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explains every hero death of a match from the stored combat log, tracks, items and cooldowns. */
@Slf4j
@Order(30)
@Component
@RequiredArgsConstructor
public class DeathAnalyzer implements ReplayAnalyzer {
  private final PlayerTraces playerTraces;
  private final ReplayPlayerRepo playerRepo;
  private final ReplayParseRunRepo runRepo;
  private final HeroStateTimelines timelines;
  private final FightRepo fightRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final DeathAnalysisRepo deathRepo;
  private final CombatLogReader combatLog;
  private final MapLayouts mapLayouts;
  private final ReplayProcessingProperties processing;
  private final ReplayAnalysisProperties properties;

  @Override
  public String name() {
    return "deaths";
  }

  @Override
  public int version() {
    return 2;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "tracks", "combat-log", "state-changes");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<CombatEvent> events = combatLog.events(matchId);
    List<ReplayPlayerDomain> roster = playerRepo.findByMatchId(matchId);
    Map<String, Integer> playerOf = CombatHeroes.playerIndexByHero(events, roster);
    if (events.isEmpty() || playerOf.isEmpty()) {
      log.info("Match {} has no combat log or heroes to explain deaths for", matchId);
      return;
    }
    Map<Integer, FightAssessor.Trace> traces = playerTraces.load(matchId);
    Map<Integer, HeroStateTimeline> states = timelines.load(matchId);
    List<FightDomain> fights = fightRepo.findByMatchId(matchId);
    Map<String, Integer> deltas = frontlineDeltas(matchId);
    MapLayout layout = layout(matchId);
    DeathAssessor assessor = new DeathAssessor(properties.deathTuning());
    Map<Integer, Integer> deathsSoFar = new HashMap<>();
    Map<String, List<CombatEvent>> byTarget =
        events.stream().collect(Collectors.groupingBy(CombatEvent::target));
    List<DeathAnalysisDomain> rows = new ArrayList<>();
    for (CombatEvent e : events) {
      if (e.kind() != CombatEventKind.DEATH
          || !e.targetIsRealHero()
          || !playerOf.containsKey(e.target())) {
        continue;
      }
      int victim = playerOf.get(e.target());
      DeathAssessor.Death death =
          new DeathAssessor.Death(e.gameSecond(), victim, e.attacker(), e.flags(), e.assistMask());
      List<CombatEvent> aboutVictim =
          byTarget.get(e.target()).stream().filter(x -> x.gameSecond() <= e.gameSecond()).toList();
      DeathAssessor.Assessment a =
          assessor.assess(
              death,
              traces,
              states.getOrDefault(victim, new HeroStateTimeline()),
              aboutVictim,
              layout,
              deltas.get(fightKey(fightIndex(fights, e.gameSecond()), victim)));
      int index = deathsSoFar.merge(victim, 1, Integer::sum) - 1;
      rows.add(row(matchId, index, death, a, fightIndex(fights, e.gameSecond())));
    }
    deathRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    deathRepo.deleteByMatchId(matchId);
  }

  private MapLayout layout(long matchId) {
    return runRepo
        .findByMatchIdAndParserVersion(matchId, processing.getParserVersion())
        .map(ReplayParseRunDomain::getMapVersion)
        .flatMap(mapLayouts::forVersion)
        .orElse(null);
  }

  private Map<String, Integer> frontlineDeltas(long matchId) {
    Map<String, Integer> deltas = new HashMap<>();
    for (FightPlayerDomain p : fightPlayerRepo.findByMatchId(matchId)) {
      if (p.getFrontlineDelta() != null) {
        deltas.put(
            fightKey(p.getFightIndex(), PlayerSlots.index(p.getPlayerSlot())),
            p.getFrontlineDelta());
      }
    }
    return deltas;
  }

  private static String fightKey(Integer fightIndex, int player) {
    return fightIndex + ":" + player;
  }

  private static Integer fightIndex(List<FightDomain> fights, double second) {
    return fights.stream()
        .filter(f -> second >= f.getStartSeconds() - 1 && second <= f.getEndSeconds() + 1)
        .map(FightDomain::getFightIndex)
        .findFirst()
        .orElse(null);
  }

  private DeathAnalysisDomain row(
      long matchId,
      int index,
      DeathAssessor.Death death,
      DeathAssessor.Assessment a,
      Integer fightIndex) {
    return DeathAnalysisDomain.builder()
        .matchId(matchId)
        .playerSlot(PlayerSlots.slot(death.victim()))
        .deathIndex(index)
        .deathSeconds(death.second())
        .killerKind(a.killerKind().name())
        .killerName(death.killer().isEmpty() ? null : death.killer())
        .assists(Integer.bitCount(death.assistMask()))
        .x(a.x())
        .y(a.y())
        .zone(a.zone() == null ? null : a.zone().kind().name())
        .lane(a.zone() == null || a.zone().lane() == null ? null : a.zone().lane().name())
        .fightIndex(fightIndex)
        .isolated(a.isolated())
        .reincarnated(a.reincarnated())
        .bkbOwned(a.bkbOwned())
        .bkbReady(a.bkbReady())
        .bkbActive(a.bkbActive())
        .bkbExpiredJustBefore(a.bkbExpiredJustBefore())
        .disableSeconds(a.disableSeconds())
        .damageLookback(a.damageLookback())
        .damageLastThreeSeconds(a.damageLastThreeSeconds())
        .topSources(a.topSources())
        .saveItemsOwned(a.saveItemsOwned())
        .teleportReady(a.teleportReady())
        .frontlineDelta(a.frontlineDelta())
        .reason(a.reason().name())
        .analysisVersion(version())
        .build();
  }
}
