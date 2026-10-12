package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class IntentNotPlayedContributorTest {
  private final IntentNotPlayedContributor contributor =
      new IntentNotPlayedContributor(
          new ReasonProperties(),
          new ModeTestKits(
              Map.of(
                  64L, ModeTestKits.hero(64, "Jakiro"),
                  1L, ModeTestKits.hero(1, "Anti-Mage")),
              Map.of()));

  private static PlayerRoleProfileDomain profile(long slot, String stage, int role) {
    return PlayerRoleProfileDomain.builder().playerSlot(slot).stage(stage).role(role).build();
  }

  private static FightPlayerDomain row(long slot, String presence) {
    return FightPlayerDomain.builder().playerSlot(slot).presence(presence).build();
  }

  private static FightContext context(
      List<PlayerRoleProfileDomain> profiles, FightPlayerDomain... rows) {
    return new FightContext(
        1,
        TestModes.ABILITY_DRAFT,
        null,
        List.of(rows),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        profiles,
        Map.of(0L, 64L, 1L, 1L),
        List.of());
  }

  @Test
  void aHeroInTheFightWhoPlayedAnotherRoleThanDraftedIsReportedWithTheDistanceAsSeverity() {
    FightContext context =
        context(
            List.of(
                profile(0, "INTENT", 1),
                profile(0, "FINAL", 2),
                profile(1, "INTENT", 5),
                profile(1, "FINAL", 1)),
            row(0, "ACTIVE"),
            row(1, "PRESENT_PASSIVE"));

    List<Reason> reasons = contributor.contribute(context);

    assertThat(reasons)
        .extracting(Reason::code, Reason::scope, Reason::subjectSlot, Reason::source)
        .containsExactly(
            tuple("INTENT_NOT_PLAYED", ReasonScope.PLAYER, 0L, "ABILITY_DRAFT"),
            tuple("INTENT_NOT_PLAYED", ReasonScope.PLAYER, 1L, "ABILITY_DRAFT"));
    assertThat(reasons.get(0).severity()).isEqualTo(0.3);
    assertThat(reasons.get(0).evidence()).isEqualTo("Jakiro was drafted as carry and played mid");
    assertThat(reasons.get(1).severity()).isEqualTo((0.3 + 0.15 * 3) * 0.5);
    assertThat(reasons.get(1).evidence())
        .isEqualTo("Anti-Mage was drafted as hard support and played carry");
  }

  @Test
  void nothingForTheSameRoleAHeroOutOfTheFightOrAMissingStage() {
    FightContext context =
        context(
            List.of(
                profile(0, "INTENT", 3),
                profile(0, "FINAL", 3),
                profile(1, "INTENT", 1),
                profile(1, "FINAL", 2),
                profile(2, "INTENT", 1)),
            row(0, "ACTIVE"),
            row(1, "ABSENT_FAR"),
            row(2, "ACTIVE"));

    assertThat(contributor.contribute(context)).isEmpty();
  }

  @Test
  void aHeroWithoutAnIdIsNamedByItsSlot() {
    FightContext context =
        context(List.of(profile(7, "INTENT", 1), profile(7, "FINAL", 4)), row(7, "ACTIVE"));

    assertThat(contributor.contribute(context))
        .extracting(Reason::evidence)
        .containsExactly("slot 7 was drafted as carry and played soft support");
  }

  @Test
  void supportsAbilityDraftOnly() {
    assertThat(contributor.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(contributor.supports(TestModes.ALL_PICK)).isFalse();
  }

  @Test
  void rolesOutsideOneToFiveGiveNoReason() {
    FightContext context =
        context(
            List.of(
                profile(0, "INTENT", 0),
                profile(0, "FINAL", 3),
                profile(1, "INTENT", 2),
                profile(1, "FINAL", 9)),
            row(0, "ACTIVE"),
            row(1, "ACTIVE"));

    assertThat(contributor.contribute(context)).isEmpty();
  }
}
