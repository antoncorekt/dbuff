package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.service.replay.analysis.defense.DefenseItemBonuses.Bonus;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DefenseMathTest {

  @Test
  void armorAddsBaseAgilityAndItems() {
    double armor = DefenseMath.armor(1, 65, 1.0 / 6, List.of(new Bonus(17, 0)));
    assertThat(armor).isCloseTo(28.83, within(0.01));
  }

  @Test
  void magicResistStacksMultiplicatively() {
    double mr = DefenseMath.magicResist(0.25, List.of(new Bonus(0, 0.18), new Bonus(0, 0.20)));
    assertThat(mr).isCloseTo(0.508, within(1e-9));
  }

  @Test
  void magicResistIsCapped() {
    double mr = DefenseMath.magicResist(0.25, List.of(new Bonus(0, 0.9), new Bonus(0, 0.9)));
    assertThat(mr).isEqualTo(0.95);
  }

  @Test
  void physicalMultiplier() {
    assertThat(DefenseMath.physicalMultiplier(0)).isEqualTo(1.0);
    assertThat(DefenseMath.physicalMultiplier(10)).isCloseTo(1 - 0.6 / 1.6, within(1e-9));
    assertThat(DefenseMath.physicalMultiplier(30)).isCloseTo(1 - 1.8 / 2.8, within(1e-9));
    assertThat(DefenseMath.physicalMultiplier(-5)).isCloseTo(1 + 0.3 / 1.3, within(1e-9));
  }

  @Test
  void ehpMixedReducesToTheSingleTypes() {
    double armor = 12;
    double mr = 0.5;
    assertThat(DefenseMath.ehpMixed(3000, armor, mr, 1, 0, 0))
        .isCloseTo(DefenseMath.ehpPhysical(3000, armor), within(1e-9));
    assertThat(DefenseMath.ehpMixed(3000, armor, mr, 0, 1, 0))
        .isCloseTo(DefenseMath.ehpMagical(3000, mr), within(1e-9));
    assertThat(DefenseMath.ehpMixed(3000, armor, mr, 0, 0, 1)).isCloseTo(3000, within(1e-9));
  }

  @Test
  void ehpMagicalCapsResistance() {
    assertThat(DefenseMath.ehpMagical(1000, 0.99)).isCloseTo(20000, within(1e-6));
  }
}
