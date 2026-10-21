package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.model.SkillEffectDomain;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ReplayCastSnapshotRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.dao.repo.SkillEffectRepo;
import com.ako.dbuff.service.replay.CastSnapshot;
import com.ako.dbuff.service.replay.CastSnapshotCodec;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.health.HeroHealth;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.skill.SkillAspect.Outcome;
import com.ako.dbuff.service.replay.analysis.skill.SkillContext.Hero;
import com.ako.dbuff.service.replay.analysis.skill.SkillContext.Match;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Judges the casts of the skills in {@code replay/skills.yaml} for every hero who has one, with the
 * aspects each entry lists, into one {@code skill_effect} row per hero and entry. A new skill is a
 * new entry; a new kind of judgment is a new aspect bean.
 */
@Order(88)
@Component
@RequiredArgsConstructor
public class SkillEffectAnalyzer implements ReplayAnalyzer {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final SkillCatalogFile file;
  private final List<SkillAspect> aspects;
  private final ReplayProcessingProperties processing;
  private final CombatLogReader combatLog;
  private final ReplayPlayerRepo playerRepo;
  private final ReplayPlayerTrackRepo trackRepo;
  private final ReplayStateChangeRepo stateRepo;
  private final FightRepo fightRepo;
  private final ReplayCastSnapshotRepo snapshotRepo;
  private final HeroKits kits;
  private final SkillEffectRepo repo;

  @Override
  public String name() {
    return "skill-effects";
  }

  @Override
  public int version() {
    return 4;
  }

  @Override
  public Set<String> requires() {
    return Set.of(
        "players", "tracks", "state-changes", "combat-log", "fights", "deaths", "cast-snapshots");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<CombatEvent> events = combatLog.events(matchId);
    Map<Long, ReplayPlayerTrackDomain> tracks = new HashMap<>();
    trackRepo.findByMatchId(matchId).forEach(t -> tracks.putIfAbsent(t.getPlayerSlot(), t));
    Map<Long, List<ReplayStateChangeDomain>> changes = new HashMap<>();
    stateRepo
        .findByMatchIdOrderByGameSeconds(matchId)
        .forEach(c -> changes.computeIfAbsent(c.getPlayerSlot(), k -> new ArrayList<>()).add(c));
    Match match =
        new Match(
            heroes(events, playerRepo.findByMatchId(matchId), tracks),
            events,
            fightRepo.findByMatchId(matchId),
            name -> kits.ability(name),
            Suppliers.memoize(() -> snapshots(matchId)));
    repo.saveAll(judge(matchId, match, changes));
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }

  private List<CastSnapshot> snapshots(long matchId) {
    return snapshotRepo
        .findByMatchId(matchId)
        .map(s -> CastSnapshotCodec.decode(s.getData()))
        .orElse(List.of());
  }

  private static List<Hero> heroes(
      List<CombatEvent> events,
      List<ReplayPlayerDomain> players,
      Map<Long, ReplayPlayerTrackDomain> tracks) {
    Set<String> names = CombatHeroes.names(events);
    List<Hero> heroes = new ArrayList<>();
    for (ReplayPlayerDomain player : players) {
      String name = CombatHeroes.nameOf(player, names);
      if (name != null
          && player.getPlayerSlot() != null
          && player.getReplayPlayerId() != null
          && player.getTeamNum() != null) {
        ReplayPlayerTrackDomain track = tracks.get(player.getPlayerSlot());
        heroes.add(
            new Hero(
                player.getPlayerSlot(),
                player.getReplayPlayerId(),
                name,
                player.getTeamNum(),
                track,
                new HeroHealth(name, events, track)));
      }
    }
    return heroes;
  }

  /** One row per hero that has a skill of an entry, in slot order. */
  List<SkillEffectDomain> judge(
      long matchId, Match match, Map<Long, List<ReplayStateChangeDomain>> changes) {
    List<SkillCatalogEntry> entries = file.entries();
    List<List<SkillAspect>> chosen = validate();
    List<SkillEffectDomain> rows = new ArrayList<>();
    for (Hero hero :
        match.heroes().stream().sorted(Comparator.comparingLong(Hero::slot)).toList()) {
      List<ReplayStateChangeDomain> own = changes.getOrDefault(hero.slot(), List.of());
      for (int i = 0; i < entries.size(); i++) {
        SkillCatalogEntry entry = entries.get(i);
        SkillCasts casts = SkillCasts.of(hero.name(), match.events(), own, entry.abilities());
        if (casts.casts().isEmpty() && !casts.everLearned(entry.abilities())) {
          continue;
        }
        rows.add(row(matchId, new SkillContext(match, entry, hero, casts), chosen.get(i)));
      }
    }
    return rows;
  }

  private SkillEffectDomain row(long matchId, SkillContext context, List<SkillAspect> aspects) {
    List<Outcome> outcomes = new ArrayList<>();
    Map<String, Integer> figures = new LinkedHashMap<>();
    for (SkillAspect aspect : aspects) {
      aspect.outcomes().forEach(o -> figures.putIfAbsent(o, 0));
      outcomes.addAll(aspect.judge(context));
    }
    outcomes.sort(Comparator.comparingDouble(Outcome::second));
    outcomes.forEach(o -> figures.merge(o.outcome(), 1, Integer::sum));
    Map<String, Integer> counts = new LinkedHashMap<>(figures);
    aspects.forEach(aspect -> figures.putAll(aspect.derivedFigures(counts)));
    List<Map<String, Object>> details = new ArrayList<>();
    for (Outcome o : outcomes) {
      Map<String, Object> detail = new LinkedHashMap<>();
      detail.put("second", o.second());
      detail.put("target", o.target());
      detail.put("outcome", o.outcome());
      detail.put("numbers", o.numbers());
      details.add(detail);
    }
    return SkillEffectDomain.builder()
        .matchId(matchId)
        .playerSlot(context.caster().slot())
        .entryKey(context.entry().key())
        .castCount((int) outcomes.stream().filter(Outcome::cast).count())
        .figures(json(figures))
        .details(json(details))
        .analysisVersion(version())
        .build();
  }

  private static String json(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot write skill effect JSON", e);
    }
  }

  @PostConstruct
  void checkCatalog() {
    validate();
  }

  /**
   * Every entry's aspects, or one error naming every entry that lists an unknown aspect, sets a
   * parameter no aspect reads, or has an ability whose aspect reads the cast snapshots while the
   * parser does not record that ability.
   */
  List<List<SkillAspect>> validate() {
    Map<String, SkillAspect> byName =
        aspects.stream().collect(Collectors.toMap(SkillAspect::name, Function.identity()));
    List<String> problems = new ArrayList<>();
    List<List<SkillAspect>> chosen = new ArrayList<>();
    Set<String> known =
        aspects.stream().flatMap(a -> a.parameterNames().stream()).collect(Collectors.toSet());
    file.defaults().keySet().stream()
        .sorted()
        .filter(name -> !known.contains(name))
        .forEach(name -> problems.add("parameters: unknown parameter '%s'".formatted(name)));
    for (SkillCatalogEntry entry : file.entries()) {
      List<SkillAspect> own = new ArrayList<>();
      Set<String> listed = new HashSet<>();
      for (String name : entry.aspects()) {
        Optional<SkillAspect> aspect = Optional.ofNullable(byName.get(name));
        if (!listed.add(name)) {
          problems.add("entry '%s': aspect '%s' is listed twice".formatted(entry.key(), name));
        } else if (aspect.isEmpty()) {
          problems.add("entry '%s': unknown aspect '%s'".formatted(entry.key(), name));
        } else {
          own.add(aspect.get());
        }
      }
      chosen.add(own);
      entry.parameters().keySet().stream()
          .sorted()
          .filter(name -> !known.contains(name))
          .filter(name -> !file.defaults().containsKey(name))
          .forEach(
              name ->
                  problems.add("entry '%s': unknown parameter '%s'".formatted(entry.key(), name)));
      if (own.stream().anyMatch(SkillAspect::needsSnapshots)) {
        entry.abilities().stream()
            .filter(ability -> !processing.getSnapshotAbilities().contains(ability))
            .forEach(
                ability ->
                    problems.add(
                        ("entry '%s': ability '%s' needs cast snapshots but is not in"
                                + " replay.processing.snapshot-abilities")
                            .formatted(entry.key(), ability)));
      }
    }
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Skill catalog: " + String.join("; ", problems));
    }
    return chosen;
  }
}
