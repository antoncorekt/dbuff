package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.dao.model.MissingDefenseItemDomain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissingDefenseWordingTest {
  private final MissingDefenseWording wording =
      new MissingDefenseWording(new MissingDefenseProperties());

  private MissingDefenseItemDomain row(long withGames, long withoutGames) {
    return MissingDefenseItemDomain.builder()
        .team("RADIANT")
        .kind("MAGIC")
        .minute(20)
        .enemyShare(0.62)
        .withGames(withGames)
        .withWins(9L)
        .withoutGames(withoutGames)
        .withoutWins(5L)
        .build();
  }

  @Test
  void comparisonSentenceWhenBothSidesHaveEnoughGames() {
    assertThat(wording.line(row(14, 19)))
        .isEqualTo(
            "By 20:00 Radiant took 62% magical damage and had no magic defence item (Pipe,"
                + " Eternal Shroud, BKB...). In stored games: with one 9 of 14 won, without 5 of"
                + " 19.");
  }

  @Test
  void notEnoughGamesSentenceWhenEitherSideIsBelowTheMinimum() {
    assertThat(wording.line(row(14, 9)))
        .endsWith(
            "had no magic defence item (Pipe, Eternal Shroud, BKB...)."
                + " Not enough stored games to compare.");
    assertThat(wording.line(row(3, 19))).endsWith("Not enough stored games to compare.");
  }

  @Test
  void physicalRowNamesPhysicalItems() {
    MissingDefenseItemDomain physical = row(14, 19);
    physical.setKind("PHYSICAL");
    physical.setTeam("DIRE");
    assertThat(wording.line(physical))
        .startsWith("By 20:00 Dire took 62% physical damage and had no physical defence item (");
  }
}
