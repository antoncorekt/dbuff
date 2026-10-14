package com.ako.dbuff.service.replay.analysis.outlier;

import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncomeSourceTest {
  private static ReplayPlayerMinuteDomain row(int minute, Long lane, Long jungle, Long kills) {
    return ReplayPlayerMinuteDomain.builder()
        .gameMinute(minute)
        .creepKillGold(lane)
        .neutralKillGold(jungle)
        .heroKillGold(kills)
        .build();
  }

  @Test
  void theLargestGainBetweenTheTwoMinutesWins() {
    var rows = List.of(row(5, 100L, 100L, 0L), row(10, 400L, 900L, 300L));

    var main = IncomeSource.main(rows, 5, 10).orElseThrow();

    assertThat(main.source()).isEqualTo(IncomeSource.JUNGLE_CREEPS);
    assertThat(main.share()).isEqualTo(800.0 / 1400);
  }

  @Test
  void aTieGoesToTheFirstSource() {
    var rows = List.of(row(0, 0L, 0L, 0L), row(5, 500L, 500L, 500L));

    assertThat(IncomeSource.main(rows, 0, 5).orElseThrow().source())
        .isEqualTo(IncomeSource.LANE_CREEPS);
  }

  @Test
  void missingCountersCountAsZeroAndNoGainIsNoSource() {
    var rows = List.of(row(0, null, null, null), row(5, null, 300L, null));

    assertThat(IncomeSource.main(rows, 0, 5).orElseThrow().source())
        .isEqualTo(IncomeSource.JUNGLE_CREEPS);
    assertThat(IncomeSource.main(List.of(row(0, null, null, null), row(5, null, null, null)), 0, 5))
        .isEmpty();
  }

  @Test
  void noRowAtTheStartOrNoRowsIsNoSource() {
    assertThat(IncomeSource.main(List.of(), 0, 5)).isEmpty();
    assertThat(IncomeSource.main(List.of(row(8, 1L, 1L, 1L), row(9, 5L, 5L, 5L)), 3, 9)).isEmpty();
  }

  @Test
  void theEndMinuteTakesTheLatestRowAtOrBefore() {
    var rows = List.of(row(0, 0L, 0L, 0L), row(5, 0L, 0L, 700L));

    assertThat(IncomeSource.main(rows, 0, 99).orElseThrow().source())
        .isEqualTo(IncomeSource.KILLS_AND_ASSISTS);
  }
}
