package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.Buybacks;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.HeroNames;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.analysis.death.DeathAssessor;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the buybacks of two All Pick matches against OpenDota's buyback logs. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class BuybackReplayTest {

  @Test
  void everyBuybackOpenDotaLogsIsFoundWithItsPlayerAndTime() {
    assertBuybacks(
        "9040892342.dem", new double[][] {{1, 1496}, {2, 1481}, {3, 1596}, {4, 1591}, {7, 1566}});
    assertBuybacks("9040889566.dem", new double[][] {{1, 1356}, {6, 1501}, {8, 1363}});
  }

  @Test
  void theSamplesStayInStepWithTheCombatLogThroughPausesOfTheGame() {
    ReplayFacts facts =
        new ClarityReplayParser()
            .parse(Path.of(System.getenv("REPLAY_FIXTURE_DIR"), "9040889566.dem"), () -> false);

    double lastSample =
        facts.samples().startSeconds()
            + (double) facts.samples().stepSeconds()
                * facts.samples().series().getFirst().x().length;
    double lastLogged = facts.combatLog().events().getLast().gameSecond();

    assertThat(lastSample)
        .as("the match has about 218 s of pauses")
        .isBetween(lastLogged - 20, lastLogged + 20);
  }

  @Test
  void theHeroDeathsOfEachPlayerEqualOpenDotasInMatchesWithBuybacks() {
    assertDeaths("9040892342.dem", new int[] {3, 8, 13, 9, 12, 5, 1, 4, 3, 5});
    assertDeaths("9040889566.dem", new int[] {1, 4, 9, 4, 4, 10, 9, 10, 13, 10});
  }

  private static void assertDeaths(String file, int[] openDota) {
    ReplayFacts facts =
        new ClarityReplayParser()
            .parse(Path.of(System.getenv("REPLAY_FIXTURE_DIR"), file), () -> false);
    List<CombatEvent> events = facts.combatLog().events();
    var heroNames = CombatHeroes.names(events);
    int[] deaths = new int[10];
    facts
        .players()
        .forEach(
            p -> {
              String hero =
                  heroNames.stream()
                      .filter(n -> HeroNames.matches(p.heroEntityName(), n))
                      .findFirst()
                      .orElseThrow();
              FightAssessor.Trace trace =
                  facts.samples().series().stream()
                      .filter(x -> x.replayPlayerId() == p.replayPlayerId())
                      .findFirst()
                      .map(
                          x ->
                              new FightAssessor.Trace(
                                  facts.samples().startSeconds(),
                                  facts.samples().stepSeconds(),
                                  x.x(),
                                  x.y(),
                                  x.level(),
                                  x.flags()))
                      .orElseThrow();
              deaths[p.replayPlayerId() / 2] =
                  (int)
                      events.stream()
                          .filter(
                              e ->
                                  e.kind() == CombatEventKind.DEATH
                                      && e.targetIsRealHero()
                                      && e.target().equals(hero))
                          .filter(e -> !aegis(e, trace, facts, p.replayPlayerId() / 2))
                          .count();
            });

    assertThat(deaths).as(file).isEqualTo(openDota);
  }

  /** Back where he died five seconds on, and not by a buyback before his next death: an Aegis. */
  private static boolean aegis(
      CombatEvent death, FightAssessor.Trace trace, ReplayFacts facts, int player) {
    double next =
        facts.combatLog().events().stream()
            .filter(
                e ->
                    e.kind() == CombatEventKind.DEATH
                        && e.targetIsRealHero()
                        && e.target().equals(death.target())
                        && e.gameSecond() > death.gameSecond())
            .mapToDouble(CombatEvent::gameSecond)
            .min()
            .orElse(Double.MAX_VALUE);
    boolean bought =
        Buybacks.of(facts.combatLog().events()).stream()
            .anyMatch(
                b -> b.player() == player && b.second() >= death.gameSecond() && b.second() < next);
    return DeathAssessor.cameBackWhereHeDied(trace, death.gameSecond(), 5) && !bought;
  }

  private static void assertBuybacks(String file, double[][] openDota) {
    ReplayFacts facts =
        new ClarityReplayParser()
            .parse(Path.of(System.getenv("REPLAY_FIXTURE_DIR"), file), () -> false);

    List<Buybacks.Buyback> found = Buybacks.of(facts.combatLog().events());

    assertThat(found).as(file).hasSize(openDota.length);
    for (double[] expected : openDota) {
      assertThat(found)
          .as("%s: player %.0f at %.0f", file, expected[0], expected[1])
          .anyMatch(
              b -> b.player() == (int) expected[0] && Math.abs(b.second() - expected[1]) <= 1.5);
    }
  }
}
