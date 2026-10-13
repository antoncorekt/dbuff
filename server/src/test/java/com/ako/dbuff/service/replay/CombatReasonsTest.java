package com.ako.dbuff.service.replay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CombatReasonsTest {

  @Test
  void goldReason_namesTheNumbersSeenInRealGamesAndFallsBackToUnknown() {
    assertThat(GoldReason.of(0)).isEqualTo(GoldReason.UNSPECIFIED);
    assertThat(GoldReason.of(1)).isEqualTo(GoldReason.DEATH);
    assertThat(GoldReason.of(11)).isEqualTo(GoldReason.BUILDING);
    assertThat(GoldReason.of(12)).isEqualTo(GoldReason.HERO_KILL);
    assertThat(GoldReason.of(13)).isEqualTo(GoldReason.CREEP_KILL);
    assertThat(GoldReason.of(14)).isEqualTo(GoldReason.NEUTRAL_KILL);
    assertThat(GoldReason.of(15)).isEqualTo(GoldReason.BOSS_KILL);
    assertThat(GoldReason.of(17)).isEqualTo(GoldReason.BOUNTY_RUNE);
    assertThat(GoldReason.of(20)).isEqualTo(GoldReason.WARD_KILL);
    assertThat(GoldReason.of(6)).isEqualTo(GoldReason.UNKNOWN);
    assertThat(GoldReason.of(19)).isEqualTo(GoldReason.UNKNOWN);
    assertThat(GoldReason.of(999)).isEqualTo(GoldReason.UNKNOWN);
  }

  @Test
  void xpReason_namesTheNumbersSeenInRealGamesAndFallsBackToUnknown() {
    assertThat(XpReason.of(1)).isEqualTo(XpReason.HERO_KILL);
    assertThat(XpReason.of(2)).isEqualTo(XpReason.CREEP_KILL);
    assertThat(XpReason.of(3)).isEqualTo(XpReason.ROSHAN_KILL);
    assertThat(XpReason.of(4)).isEqualTo(XpReason.WISDOM_RUNE);
    assertThat(XpReason.of(8)).isEqualTo(XpReason.UNKNOWN);
  }

  @Test
  void combatEvent_readsTheReasonFromAux_andAnOldBlobsZeroIsUnspecified() {
    CombatEvent gold =
        new CombatEvent(CombatEventKind.GOLD, 1, "", "h", "", 0, 40, 0, 17, 0, 0, 0, 0);
    CombatEvent oldXp =
        new CombatEvent(CombatEventKind.XP, 1, "", "h", "", 0, 40, 0, 0, 0, 0, 0, 0);

    assertThat(gold.goldReason()).isEqualTo(GoldReason.BOUNTY_RUNE);
    assertThat(oldXp.xpReason()).isEqualTo(XpReason.UNSPECIFIED);
  }
}
