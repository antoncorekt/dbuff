package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.rune.RuneProperties;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RuneAdvantageReasonContributorTest {
  private final RuneAdvantageReasonContributor contributor =
      new RuneAdvantageReasonContributor(
          new ReasonProperties(),
          new RuneProperties(),
          new ModeTestKits(
              Map.of(1L, ModeTestKits.hero(1, "Windrunner"), 2L, ModeTestKits.hero(2, "Axe")),
              Map.of()));

  private static RunePickupDomain rune(long slot, String type, int seconds) {
    return RunePickupDomain.builder().playerSlot(slot).runeType(type).seconds(seconds).build();
  }

  private static FightPlayerDomain row(long slot, String presence) {
    return FightPlayerDomain.builder().playerSlot(slot).presence(presence).build();
  }

  private static FightContext context(List<RunePickupDomain> runes, FightPlayerDomain... players) {
    return new FightContext(
        1,
        TestModes.ALL_PICK,
        FightDomain.builder().startSeconds(600.0).endSeconds(640.0).build(),
        List.of(players),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Map.of(0L, 1L, 128L, 2L),
        List.of(),
        runes);
  }

  @Test
  void aPowerRuneStillActiveAtTheStartGivesTheTakersTeamAnAdvantageAndTheOtherTeamsTheEnemys() {
    List<Reason> reasons =
        contributor.contribute(context(List.of(rune(0, "DOUBLE_DAMAGE", 560)), row(0, "ACTIVE")));

    assertThat(reasons)
        .extracting(
            Reason::code, Reason::scope, Reason::subjectSlot, Reason::team, Reason::severity)
        .containsExactly(
            tuple("RUNE_ADVANTAGE", ReasonScope.PLAYER, 0L, TeamSide.RADIANT, 0.5),
            tuple("ENEMY_RUNE_ADVANTAGE", ReasonScope.TEAM, null, TeamSide.DIRE, 0.5));
    assertThat(reasons.get(0).evidence())
        .isEqualTo(
            "double damage rune taken at 9:20 by Windrunner, 40 s from the fight start, active in"
                + " the fight");
  }

  @Test
  void aDireTakerGivesTheEnemyReasonToRadiant() {
    List<Reason> reasons =
        contributor.contribute(context(List.of(rune(128, "HASTE", 595)), row(128, "ACTIVE")));

    assertThat(reasons)
        .extracting(Reason::code, Reason::team)
        .containsExactly(
            tuple("RUNE_ADVANTAGE", TeamSide.DIRE),
            tuple("ENEMY_RUNE_ADVANTAGE", TeamSide.RADIANT));
  }

  @Test
  void aRuneTakenInsideTheFightCounts() {
    assertThat(contributor.contribute(context(List.of(rune(0, "ARCANE", 620)), row(0, "ACTIVE"))))
        .hasSize(2);
  }

  @Test
  void nothingWhenTheEffectEndedBeforeTheFightOrBeganAfterIt() {
    assertThat(contributor.contribute(context(List.of(rune(0, "HASTE", 560)), row(0, "ACTIVE"))))
        .as("haste lasts 22 s")
        .isEmpty();
    assertThat(
            contributor.contribute(
                context(List.of(rune(0, "DOUBLE_DAMAGE", 641)), row(0, "ACTIVE"))))
        .isEmpty();
  }

  @Test
  void nothingForARuneWithoutAFightEffectOrATakerOutOfTheFight() {
    assertThat(
            contributor.contribute(
                context(
                    List.of(rune(0, "BOUNTY", 590), rune(0, "WATER", 590), rune(0, "WISDOM", 590)),
                    row(0, "ACTIVE"))))
        .isEmpty();
    assertThat(
            contributor.contribute(
                context(List.of(rune(0, "DOUBLE_DAMAGE", 590)), row(0, "ABSENT_FAR"))))
        .isEmpty();
    assertThat(contributor.contribute(context(List.of(rune(0, "DOUBLE_DAMAGE", 590))))).isEmpty();
  }

  @Test
  void supportsEveryMode() {
    assertThat(contributor.supports(TestModes.ALL_PICK)).isTrue();
    assertThat(contributor.supports(TestModes.ABILITY_DRAFT)).isTrue();
  }
}
