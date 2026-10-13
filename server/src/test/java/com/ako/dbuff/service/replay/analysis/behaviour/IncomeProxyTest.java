package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.AwayTrip.End;
import com.ako.dbuff.service.replay.analysis.lane.MinuteSeries;
import java.util.Optional;
import java.util.function.IntToLongFunction;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.MID;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.TOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IncomeProxyTest {
  private final IncomeProxy proxy = new IncomeProxy(60);

  private static MinuteSeries series(int fromMinute, int toMinute, IntToLongFunction total) {
    MinuteSeries series = new MinuteSeries();
    for (int minute = fromMinute; minute <= toMinute; minute++) {
      series.put(minute, total.applyAsLong(minute));
    }
    return series;
  }

  /** 500 gold a minute up to minute 10, then 200. */
  private static long slowingGold(int minute) {
    return minute <= 10 ? 500L * minute : 5000 + 200L * (minute - 10);
  }

  private static AwayTrip trip(int start, int end, End how) {
    return new AwayTrip(0L, start, start, end, MID, TOP, MID, false, how);
  }

  @Test
  void of_comparesTheTripAndItsTailWithTheSameStretchBefore() {
    MinuteSeries gold = series(0, 20, IncomeProxyTest::slowingGold);
    MinuteSeries xp = series(0, 20, m -> 400L * m);

    Income income = proxy.of(trip(600, 660, End.REJOINED), gold, xp).orElseThrow();

    assertThat(income.goldBefore()).isCloseTo(500, within(0.01));
    assertThat(income.goldDuring()).isCloseTo(200, within(0.01));
    assertThat(income.xpBefore()).isCloseTo(400, within(0.01));
    assertThat(income.xpDuring()).isCloseTo(400, within(0.01));
    assertThat(income.dropped(0.7)).isTrue();
  }

  @Test
  void of_readsBetweenTheMinutes() {
    MinuteSeries gold = series(0, 20, IncomeProxyTest::slowingGold);

    Income income = proxy.of(trip(630, 690, End.REJOINED), gold, gold).orElseThrow();

    assertThat(income.goldBefore()).isCloseTo(425, within(0.01));
    assertThat(income.goldDuring()).isCloseTo(200, within(0.01));
  }

  @Test
  void of_aDeathHasNoTailBecauseDeadTimeEarnsNothing() {
    MinuteSeries gold = series(0, 20, IncomeProxyTest::slowingGold);

    Income income = proxy.of(trip(600, 660, End.DIED), gold, gold).orElseThrow();

    assertThat(income.goldDuring()).isCloseTo(200, within(0.01));
    assertThat(proxy.of(trip(540, 570, End.DIED), gold, gold).orElseThrow().goldDuring())
        .isCloseTo(500, within(0.01));
  }

  @Test
  void of_nothingBeforeTheStartOfTheGame() {
    MinuteSeries gold = series(0, 20, IncomeProxyTest::slowingGold);

    assertThat(proxy.of(trip(60, 90, End.REJOINED), gold, gold)).isEmpty();
    assertThat(proxy.of(trip(190, 220, End.REJOINED), gold, gold)).isNotEmpty();
  }

  @Test
  void of_nothingWhenTheTotalsEndBeforeTheTail() {
    MinuteSeries gold = series(0, 11, IncomeProxyTest::slowingGold);

    assertThat(proxy.of(trip(600, 660, End.REJOINED), gold, gold)).isEmpty();
    assertThat(proxy.of(trip(600, 660, End.DIED), gold, gold)).isNotEmpty();
  }

  @Test
  void of_nothingWithoutASeries() {
    MinuteSeries gold = series(0, 20, IncomeProxyTest::slowingGold);

    assertThat(proxy.of(trip(600, 660, End.REJOINED), null, gold)).isEmpty();
    assertThat(proxy.of(trip(600, 660, End.REJOINED), gold, new MinuteSeries())).isEmpty();
  }

  @Test
  void dropped_needsARealReferenceAndAFallToTheShareOrBelow() {
    assertThat(new Income(500, 350, 0, 0).dropped(0.7)).isTrue();
    assertThat(new Income(500, 351, 0, 0).dropped(0.7)).isFalse();
    assertThat(new Income(0, 0, 0, 0).dropped(0.7)).isFalse();
  }

  @Test
  void of_aMissingMinuteInsideTheStretchIsBridgedByTheNeighbours() {
    MinuteSeries gold = new MinuteSeries();
    for (int minute : new int[] {0, 5, 9, 12, 14}) {
      gold.put(minute, slowingGold(minute));
    }

    Optional<Income> income = proxy.of(trip(600, 660, End.REJOINED), gold, gold);

    assertThat(income.orElseThrow().goldBefore()).isCloseTo(400, within(0.01));
  }
}
