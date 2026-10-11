package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimelines;
import java.util.ArrayList;
import java.util.EnumSet;
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
 * For each fight, says what the heroes who took part did with their abilities and items, and when
 * they did not use one, why not. Runs after {@link FightAnalyzer}, whose fights it reads.
 */
@Slf4j
@Order(25)
@Component
@RequiredArgsConstructor
public class FightAbilityAnalyzer implements ReplayAnalyzer {
  private static final Set<String> TOOK_PART = Set.of("ACTIVE", "PRESENT_PASSIVE");
  private static final Set<CombatEventKind> HERO_EFFECTS =
      EnumSet.of(CombatEventKind.ABILITY, CombatEventKind.ITEM, CombatEventKind.DAMAGE);

  private final ReplayPlayerTrackRepo trackRepo;
  private final ReplayPlayerRepo playerRepo;
  private final FightRepo fightRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final FightAbilityUseRepo useRepo;
  private final HeroStateTimelines timelines;
  private final CombatLogReader combatLog;
  private final ReplayAnalysisProperties properties;

  @Override
  public String name() {
    return "fight-abilities";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "tracks", "combat-log", "state-changes");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<FightDomain> fights = fightRepo.findByMatchId(matchId);
    List<CombatEvent> events = combatLog.events(matchId);
    List<ReplayPlayerDomain> roster = playerRepo.findByMatchId(matchId);
    Map<String, Integer> playerOf = CombatHeroes.playerIndexByHero(events, roster);
    if (fights.isEmpty() || events.isEmpty() || playerOf.isEmpty()) {
      return;
    }
    Map<Integer, Map<String, List<CombatEvent>>> effects = effects(events, playerOf);
    Map<Integer, List<Double>> deaths = deaths(events, playerOf);
    Map<Integer, ReplayPlayerTrackDomain> tracks = tracks(matchId);
    Map<Integer, HeroStateTimeline> states = timelines.load(matchId);
    FightAbilityAssessor assessor =
        new FightAbilityAssessor(properties.getFightEarlyDeathSeconds());
    Map<Integer, FightDomain> fightByIndex = new HashMap<>();
    fights.forEach(f -> fightByIndex.put(f.getFightIndex(), f));
    List<FightAbilityUseDomain> rows = new ArrayList<>();
    for (FightPlayerDomain part : fightPlayerRepo.findByMatchId(matchId)) {
      int player = PlayerSlots.index(part.getPlayerSlot());
      HeroStateTimeline state = states.get(player);
      FightDomain fight = fightByIndex.get(part.getFightIndex());
      if (fight == null || state == null || !TOOK_PART.contains(part.getPresence())) {
        continue;
      }
      List<FightAbilityAssessor.Use> uses =
          assessor.assess(
              fight.getStartSeconds(),
              fight.getEndSeconds(),
              state,
              manaAt(tracks.get(player), fight.getStartSeconds()),
              effects.getOrDefault(player, Map.of()),
              deaths.getOrDefault(player, List.of()));
      uses.forEach(use -> rows.add(row(matchId, part, use)));
    }
    useRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    useRepo.deleteByMatchId(matchId);
  }

  private static Map<Integer, Map<String, List<CombatEvent>>> effects(
      List<CombatEvent> events, Map<String, Integer> playerOf) {
    Map<Integer, Map<String, List<CombatEvent>>> byPlayer = new HashMap<>();
    for (CombatEvent e : events) {
      Integer player = playerOf.get(e.attacker());
      if (HERO_EFFECTS.contains(e.kind()) && e.attackerIsRealHero() && player != null) {
        byPlayer
            .computeIfAbsent(player, k -> new HashMap<>())
            .computeIfAbsent(e.inflictor(), k -> new ArrayList<>())
            .add(e);
      }
    }
    return byPlayer;
  }

  private static Map<Integer, List<Double>> deaths(
      List<CombatEvent> events, Map<String, Integer> playerOf) {
    Map<Integer, List<Double>> byPlayer = new HashMap<>();
    for (CombatEvent e : events) {
      Integer player = playerOf.get(e.target());
      if (e.kind() == CombatEventKind.DEATH && e.targetIsRealHero() && player != null) {
        byPlayer.computeIfAbsent(player, k -> new ArrayList<>()).add(e.gameSecond());
      }
    }
    return byPlayer;
  }

  private Map<Integer, ReplayPlayerTrackDomain> tracks(long matchId) {
    Map<Integer, ReplayPlayerTrackDomain> tracks = new HashMap<>();
    trackRepo
        .findByMatchId(matchId)
        .forEach(t -> tracks.put(PlayerSlots.index(t.getPlayerSlot()), t));
    return tracks;
  }

  /** Null when the hero has no track, was not alive in the sample, or the track predates mana. */
  private static Integer manaAt(ReplayPlayerTrackDomain track, double second) {
    if (track == null || track.getMana() == null || track.getMana().length == 0) {
      return null;
    }
    int i = (int) Math.round((second - track.getStartSeconds()) / track.getStepSeconds());
    i = Math.max(0, Math.min(track.getMana().length - 1, i));
    return (track.getFlags()[i] & 1) == 0 ? null : track.getMana()[i];
  }

  private FightAbilityUseDomain row(
      long matchId, FightPlayerDomain part, FightAbilityAssessor.Use use) {
    return FightAbilityUseDomain.builder()
        .matchId(matchId)
        .fightIndex(part.getFightIndex())
        .playerSlot(part.getPlayerSlot())
        .abilityName(use.name())
        .kind(use.item() ? "ITEM" : "ABILITY")
        .level(use.level())
        .readyAtStart(use.cooldownLeft() == 0)
        .cooldownLeftAtStart(use.cooldownLeft())
        .manaCost(use.manaCost())
        .manaAtStart(use.manaAtStart())
        .casts(use.casts())
        .firstCastSeconds(use.firstCast())
        .damage(use.damage())
        .lastCastBeforeSeconds(use.lastCastBefore())
        .lastCastOnCreeps(use.lastCastOnCreeps())
        .nonCastReason(use.reason() == null ? null : use.reason().name())
        .analysisVersion(version())
        .build();
  }
}
