package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.HeroNames;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.fight.FightDetector;
import com.ako.dbuff.service.replay.analysis.fight.FightType;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the fight detector on a real replay against the teamfights OpenDota lists for it. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class FightReplayTest {

  /** OpenDota teamfights of match 9038725154: start, end and number of deaths. */
  private static final int[][] OPEN_DOTA = {
    {127, 169, 3},
    {365, 397, 3},
    {497, 542, 5},
    {593, 633, 3},
    {645, 687, 4},
    {881, 925, 3},
    {1024, 1061, 3},
    {1176, 1217, 3},
    {1278, 1334, 6},
    {1461, 1520, 6},
    {1614, 1673, 8},
    {1820, 1873, 4},
    {1946, 1984, 3},
    {2011, 2049, 3},
    {2044, 2081, 4},
    {2157, 2204, 5}
  };

  @Test
  void everyOpenDotaTeamfightIsFoundAsOurTeamfightWithAtLeastItsDeaths() {
    ReplayFacts facts =
        new ClarityReplayParser()
            .parse(
                Path.of(System.getenv("REPLAY_FIXTURE_DIR"), "9038725154.dem"),
                TestModes.ABILITY_DRAFT,
                () -> false);
    List<CombatEvent> events = facts.combatLog().events();
    Map<String, Integer> playerOf = new HashMap<>();
    facts
        .players()
        .forEach(
            p ->
                events.stream()
                    .filter(e -> (e.flags() & CombatEvent.ATTACKER_HERO) != 0)
                    .map(CombatEvent::attacker)
                    .distinct()
                    .filter(name -> HeroNames.matches(p.heroEntityName(), name))
                    .findFirst()
                    .ifPresent(name -> playerOf.put(name, p.replayPlayerId() / 2)));

    var fights =
        new FightDetector(new ReplayAnalysisProperties().fightTuning()).detect(events, playerOf);

    for (int[] od : OPEN_DOTA) {
      assertThat(fights)
          .as("OpenDota teamfight %d-%d with %d deaths", od[0], od[1], od[2])
          .anyMatch(
              f ->
                  f.type() == FightType.TEAMFIGHT
                      && f.start() <= od[1]
                      && f.end() >= od[0]
                      && f.deaths().size() >= od[2]);
    }
    assertThat(fights.stream().filter(f -> f.type() == FightType.TEAMFIGHT).count())
        .as("not many more teamfights than OpenDota's sixteen")
        .isBetween(14L, 22L);
  }
}
