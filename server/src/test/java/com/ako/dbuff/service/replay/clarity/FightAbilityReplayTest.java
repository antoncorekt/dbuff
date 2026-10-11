package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.HeroNames;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.fight.FightAbilityAssessor;
import com.ako.dbuff.service.replay.analysis.fight.FightDetector;
import com.ako.dbuff.service.replay.analysis.fight.FightType;
import com.ako.dbuff.service.replay.analysis.fight.NonCastReason;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Follows the first hero of match 9038725154 through its fights and checks what is said of him. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class FightAbilityReplayTest {

  @Test
  void theHeroIsSaidToHaveUsedTheAbilitiesOpenDotaCountsAndEveryMissedOneHasAReason() {
    ReplayFacts facts =
        new ClarityReplayParser()
            .parse(Path.of(System.getenv("REPLAY_FIXTURE_DIR"), "9038725154.dem"), () -> false);
    List<CombatEvent> events = facts.combatLog().events();
    Map<String, Integer> playerOf = new HashMap<>();
    var heroNames = CombatHeroes.names(events);
    facts
        .players()
        .forEach(
            p -> {
              String name =
                  heroNames.stream()
                      .filter(n -> HeroNames.matches(p.heroEntityName(), n))
                      .findFirst()
                      .orElse(null);
              if (name != null) {
                playerOf.put(name, p.replayPlayerId() / 2);
              }
            });
    String hero =
        playerOf.entrySet().stream()
            .filter(e -> e.getValue() == 0)
            .findFirst()
            .orElseThrow()
            .getKey();
    HeroStateTimeline state = new HeroStateTimeline();
    facts.stateChanges().changes().stream()
        .filter(c -> c.replayPlayerId() == 0)
        .forEach(
            c ->
                state.add(
                    new HeroStateTimeline.Change(
                        c.gameSecond(),
                        c.kind(),
                        c.name(),
                        c.readyAtSeconds(),
                        c.level(),
                        c.manaCost())));
    Map<String, List<CombatEvent>> byName =
        events.stream()
            .filter(
                e ->
                    e.attacker().equals(hero)
                        && e.attackerIsRealHero()
                        && e.kind() != CombatEventKind.DEATH)
            .collect(Collectors.groupingBy(CombatEvent::inflictor));
    List<Double> deaths =
        events.stream()
            .filter(e -> e.kind() == CombatEventKind.DEATH && e.target().equals(hero))
            .map(CombatEvent::gameSecond)
            .toList();
    var fights =
        new FightDetector(new ReplayAnalysisProperties().fightTuning()).detect(events, playerOf);
    var assessor = new FightAbilityAssessor(5);

    List<FightAbilityAssessor.Use> all = new ArrayList<>();
    for (var fight : fights) {
      if (fight.type() == FightType.TEAMFIGHT) {
        all.addAll(assessor.assess(fight.start(), fight.end(), state, null, byName, deaths));
      }
    }

    long conjures =
        all.stream()
            .filter(u -> u.name().equals("terrorblade_conjure_image"))
            .mapToInt(FightAbilityAssessor.Use::casts)
            .sum();
    assertThat(conjures).as("casts in teamfights, of OpenDota's 64 in all").isBetween(1L, 64L);
    assertThat(all)
        .filteredOn(u -> u.casts() == 0)
        .isNotEmpty()
        .allSatisfy(u -> assertThat(u.reason()).isNotNull());
    assertThat(all).filteredOn(u -> u.casts() > 0).allSatisfy(u -> assertThat(u.reason()).isNull());
    assertThat(all.stream().map(FightAbilityAssessor.Use::reason).distinct().toList())
        .as("the sample shows more than one kind of reason")
        .contains(NonCastReason.ON_COOLDOWN);
  }
}
