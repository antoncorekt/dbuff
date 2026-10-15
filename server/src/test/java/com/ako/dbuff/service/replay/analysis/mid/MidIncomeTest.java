package com.ako.dbuff.service.replay.analysis.mid;

import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MidIncomeTest {
  private static PlayerTimelineDomain minute(long slot, int minute, long gold, long xp) {
    return PlayerTimelineDomain.builder()
        .playerSlot(slot)
        .gameMinute(minute)
        .gold(gold)
        .xp(xp)
        .build();
  }

  private final MidIncome income =
      new MidIncome(
          List.of(
              minute(0, 10, 3000, 4000), minute(0, 20, 9000, 10000), minute(0, 30, 18000, 22000)));

  @Test
  void ratesAreTheGrowthOverTheWholeMinutesOfTheStretch() {
    assertThat(income.goldPerMinute(0, 600, 1200)).isEqualTo(600.0);
    assertThat(income.xpPerMinute(0, 600, 1200)).isEqualTo(600.0);
  }

  @Test
  void aStretchPastTheLastMinuteIsCutThere() {
    assertThat(income.goldPerMinute(0, 1200, 3600)).isEqualTo(900.0);
  }

  @Test
  void aMissingMinuteOrPlayerGivesNoRate() {
    assertThat(income.goldPerMinute(0, 660, 1200)).isNull();
    assertThat(income.goldPerMinute(1, 600, 1200)).isNull();
    assertThat(income.goldPerMinute(0, 1800, 1800)).isNull();
  }
}
