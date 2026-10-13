package com.ako.dbuff.service.replay.analysis.rune;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuneTypeTest {
  @Test
  void of_namesTheCodesOfTheRunesLog() {
    assertThat(RuneType.of(0)).isEqualTo(RuneType.DOUBLE_DAMAGE);
    assertThat(RuneType.of(5)).isEqualTo(RuneType.BOUNTY);
    assertThat(RuneType.of(7)).isEqualTo(RuneType.WATER);
    assertThat(RuneType.of(8)).isEqualTo(RuneType.WISDOM);
    assertThat(RuneType.of(9)).isEqualTo(RuneType.SHIELD);
  }

  @Test
  void of_readsAnUnlistedOrMissingCodeAsUnknown() {
    assertThat(RuneType.of(42)).isEqualTo(RuneType.UNKNOWN);
    assertThat(RuneType.of(null)).isEqualTo(RuneType.UNKNOWN);
  }

  @Test
  void onlyThePowerRunesChangeAFight() {
    assertThat(RuneType.values())
        .filteredOn(RuneType::isFightEffect)
        .containsExactly(
            RuneType.DOUBLE_DAMAGE,
            RuneType.HASTE,
            RuneType.INVISIBILITY,
            RuneType.ARCANE,
            RuneType.SHIELD);
  }

  @Test
  void evidence_readsBackWhatItWrote() {
    RuneEvidence written = new RuneEvidence("double damage", "5:20", "Windrunner", 40);

    assertThat(RuneEvidence.parse(written.text())).contains(written);
    assertThat(written.timing()).isEqualTo("40 s before the fight");
    assertThat(new RuneEvidence("haste", "9:00", "Axe", -7).timing())
        .isEqualTo("7 s into the fight");
    assertThat(RuneEvidence.parse("nonsense")).isEmpty();
    assertThat(RuneEvidence.parse(null)).isEmpty();
  }
}
