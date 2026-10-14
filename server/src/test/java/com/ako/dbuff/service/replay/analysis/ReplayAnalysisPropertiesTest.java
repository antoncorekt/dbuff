package com.ako.dbuff.service.replay.analysis;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayAnalysisPropertiesTest {
  private final ReplayAnalysisProperties properties = new ReplayAnalysisProperties();

  @Test
  void theDefaultsAreValid() {
    properties.validate();

    assertThat(properties.getIncomeBreakMinutes()).isEqualTo(3);
    assertThat(properties.getIncomeBreakShare()).isEqualTo(0.25);
  }

  @Test
  void rejectsMinutesOutOfRange() {
    properties.setIncomeBreakMinutes(0);
    assertThatThrownBy(properties::validate).hasMessageContaining("income-break-minutes");
    properties.setIncomeBreakMinutes(31);
    assertThatThrownBy(properties::validate).hasMessageContaining("income-break-minutes");
  }

  @Test
  void rejectsASharePastTheRangeAndNaN() {
    for (double bad : new double[] {0, -0.1, 5.1, Double.NaN, Double.POSITIVE_INFINITY}) {
      properties.setIncomeBreakShare(bad);
      assertThatThrownBy(properties::validate).hasMessageContaining("income-break-share");
    }
  }

  @Test
  void rejectsSiblingOutlierSettingsOutOfRange() {
    properties.setOutlierSharePercent(0);
    assertThatThrownBy(properties::validate).hasMessageContaining("outlier-share-percent");
    properties.setOutlierSharePercent(101);
    assertThatThrownBy(properties::validate).hasMessageContaining("outlier-share-percent");
    properties.setOutlierSharePercent(35);
    properties.setOutlierMinutes(0);
    assertThatThrownBy(properties::validate).hasMessageContaining("outlier-minutes");
    properties.setOutlierMinutes(3);
    properties.setFeederSharePercent(-1);
    assertThatThrownBy(properties::validate).hasMessageContaining("feeder-share-percent");
    properties.setFeederSharePercent(10);
    properties.setFeederMinutes(-5);
    assertThatThrownBy(properties::validate).hasMessageContaining("feeder-minutes");
  }

  @Test
  void theDefaultIgnoredItemsHoldTheNonCombatOnesAndPassValidation() {
    properties.validate();

    assertThat(properties.fightAbilityIgnoredItems())
        .contains("item_quelling_blade", "item_tango", "item_cheese", "item_tpscroll")
        .doesNotContain("item_black_king_bar");
  }

  @Test
  void rejectsAnIgnoredItemThatIsNotAnItemName() {
    for (String bad : new String[] {"quelling_blade", "item_Tango", "item_", "", " item_x"}) {
      properties.getFightAbility().setIgnoredItems(List.of(bad));
      assertThatThrownBy(properties::validate).hasMessageContaining("ignored-items");
    }
    properties.getFightAbility().setIgnoredItems(null);
    assertThatThrownBy(properties::validate).hasMessageContaining("ignored-items");
  }
}
