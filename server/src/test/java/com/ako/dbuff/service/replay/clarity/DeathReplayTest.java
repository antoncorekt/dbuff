package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.death.DeathAssessor;
import com.ako.dbuff.service.replay.analysis.death.DeathReason;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the death analysis on a real replay against OpenDota's deaths and Black King Bar data. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class DeathReplayTest {

  /** OpenDota deaths per player index of match 9038725154. */
  private static final int[] OPEN_DOTA_DEATHS = {9, 7, 10, 7, 6, 12, 11, 10, 11, 11};

  private static final int PHANTOM_ASSASSIN = 4;
  private static final double PHANTOM_BOUGHT_BKB = 1359;

  private final Map<Integer, Map<Double, DeathAssessor.Assessment>> assessments = new HashMap<>();

  @Test
  void everyDeathOpenDotaCountsIsExplained_andTheAegisUseIsTold() {
    Map<Integer, Integer> real = new TreeMap<>();
    List<String> reincarnated = new java.util.ArrayList<>();
    assess()
        .forEach(
            (victim, byTime) ->
                byTime.forEach(
                    (second, a) -> {
                      if (a.reincarnated()) {
                        reincarnated.add(victim + "@" + Math.round(second));
                      } else {
                        real.merge(victim, 1, Integer::sum);
                      }
                    }));

    for (int player = 0; player < 10; player++) {
      assertThat(real.get(player))
          .as("deaths of player %d", player)
          .isEqualTo(OPEN_DOTA_DEATHS[player]);
    }
    assertThat(reincarnated)
        .as("the Aegis use the combat log records as a death")
        .containsExactly("4@1969");
  }

  @Test
  void theBlackKingBarIsOwnedFromWhenOpenDotaSaysItWasBought() {
    var byTime = assess().get(PHANTOM_ASSASSIN);

    byTime.forEach(
        (second, a) -> {
          if (second < PHANTOM_BOUGHT_BKB - 10) {
            assertThat(a.bkbOwned()).as("death at %.0f", second).isFalse();
          } else if (second > PHANTOM_BOUGHT_BKB + 10) {
            assertThat(a.bkbOwned()).as("death at %.0f", second).isTrue();
          }
        });
    assertThat(byTime.values().stream().filter(a -> a.reason() == DeathReason.DIED_IMMUNE).count())
        .as("one death while immune")
        .isEqualTo(1);
  }

  private Map<Integer, Map<Double, DeathAssessor.Assessment>> assess() {
    if (!assessments.isEmpty()) {
      return assessments;
    }
    ReplayFacts facts =
        new ClarityReplayParser()
            .parse(Path.of(System.getenv("REPLAY_FIXTURE_DIR"), "9038725154.dem"), () -> false);
    List<CombatEvent> events = facts.combatLog().events();
    Map<String, Integer> playerOf = new HashMap<>();
    var heroNames = CombatHeroes.names(events);
    facts
        .players()
        .forEach(
            p ->
                heroNames.stream()
                    .filter(
                        n -> com.ako.dbuff.service.replay.HeroNames.matches(p.heroEntityName(), n))
                    .findFirst()
                    .ifPresent(n -> playerOf.put(n, p.replayPlayerId() / 2)));
    var samples = facts.samples();
    Map<Integer, FightAssessor.Trace> traces = new HashMap<>();
    samples
        .series()
        .forEach(
            s ->
                traces.put(
                    s.replayPlayerId() / 2,
                    new FightAssessor.Trace(
                        samples.startSeconds(),
                        samples.stepSeconds(),
                        s.x(),
                        s.y(),
                        s.level(),
                        s.flags())));
    Map<Integer, HeroStateTimeline> states = new HashMap<>();
    facts
        .stateChanges()
        .changes()
        .forEach(
            c ->
                states
                    .computeIfAbsent(c.replayPlayerId() / 2, k -> new HeroStateTimeline())
                    .add(
                        new HeroStateTimeline.Change(
                            c.gameSecond(), c.kind(), c.name(), c.readyAtSeconds())));
    var layouts = new MapLayouts(new ObjectMapper());
    var layout = layouts.forVersion(facts.map().version()).orElseThrow();
    var assessor = new DeathAssessor(new ReplayAnalysisProperties().deathTuning());
    for (CombatEvent e : events) {
      if (e.kind() != CombatEventKind.DEATH
          || !e.targetIsRealHero()
          || !playerOf.containsKey(e.target())) {
        continue;
      }
      int victim = playerOf.get(e.target());
      var about =
          events.stream()
              .filter(x -> e.target().equals(x.target()) && x.gameSecond() <= e.gameSecond())
              .toList();
      var death =
          new DeathAssessor.Death(e.gameSecond(), victim, e.attacker(), e.flags(), e.assistMask());
      assessments
          .computeIfAbsent(victim, k -> new TreeMap<>())
          .put(
              e.gameSecond(),
              assessor.assess(
                  death,
                  traces,
                  states.getOrDefault(victim, new HeroStateTimeline()),
                  about,
                  layout));
    }
    return assessments;
  }
}
