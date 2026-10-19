package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeathCostTest {
  private static final WorthPrices PRICES = new WorthPrices(10, 1, 0.02, 0.25, 8, 30);
  private static final MatchFacts MATCH =
      new MatchFacts(
          List.of(),
          Map.of(ItemTestData.HERO, 0L),
          Map.of(
              0L,
              List.of(
                  ItemTestData.minute(1, 0, 0),
                  ItemTestData.minute(2, 1200, 0),
                  ItemTestData.minute(3, 1800, 250))));

  private static HeroInputs hero(List<CombatEvent> events, ReplayPlayerTrackDomain track) {
    return ItemTestData.heroWith(
        ItemTestData.hero(List.of(), events, track, List.of(), List.of()), MATCH);
  }

  /** Dead from 125 s to 150 s: samples are 5 s apart from -90 s. */
  private static ReplayPlayerTrackDomain track() {
    ReplayPlayerTrackDomain track = ItemTestData.track(100);
    int[] flags = new int[100];
    java.util.Arrays.fill(flags, 1);
    for (int second = 125; second < 150; second += 5) {
      flags[ItemTestData.sampleOf(second)] = 0;
    }
    track.setFlags(flags);
    return track;
  }

  @Test
  void aRealDeathCostsTheGoldLostAndTheRespawnAtTheIncomeOfThatMinute() {
    DeathCost.Cost cost = new DeathCost(hero(List.of(), track()), PRICES).at(122);

    assertThat(cost.goldLost()).isEqualTo(250);
    assertThat(cost.respawnSeconds()).isEqualTo(28);
    assertThat(cost.goldPerMinute()).isEqualTo(600);
    assertThat(cost.estimated()).isFalse();
    assertThat(cost.value()).isEqualTo(250 + 28 * 600 / 60.0);
  }

  @Test
  void withoutTheTrackTheRespawnIsTheAssumedOneAndTheCostIsEstimated() {
    DeathCost.Cost cost = new DeathCost(hero(List.of(), null), PRICES).at(122);

    assertThat(cost.respawnSeconds()).isEqualTo(30);
    assertThat(cost.estimated()).isTrue();
  }

  @Test
  void anAvoidedDeathTakesTheAverageOfTheHerosRealDeaths() {
    CombatEvent death = ItemTestData.death(122);

    DeathCost.Cost cost = new DeathCost(hero(List.of(death), track()), PRICES).avoided(60);

    assertThat(cost.goldLost()).isEqualTo(250);
    assertThat(cost.respawnSeconds()).isEqualTo(28);
    assertThat(cost.estimated()).isFalse();
  }

  @Test
  void anAvoidedDeathWithoutRealOnesUsesTheGoldHeldThen() {
    ReplayPlayerTrackDomain track = track();
    track.setGold(new int[100]);
    track.getGold()[ItemTestData.sampleOf(120)] = 700;

    DeathCost.Cost cost = new DeathCost(hero(List.of(), track), PRICES).avoided(120);

    assertThat(cost.goldLost()).isEqualTo(700);
    assertThat(cost.respawnSeconds()).isEqualTo(30);
    assertThat(cost.estimated()).isTrue();
  }

  @Test
  void twoDeathsInOneMinuteShareThatMinutesGoldLost() {
    List<CombatEvent> deaths = List.of(ItemTestData.death(122), ItemTestData.death(140));

    DeathCost.Cost cost = new DeathCost(hero(deaths, track()), PRICES).at(122);

    assertThat(cost.goldLost()).isEqualTo(125);
  }
}
