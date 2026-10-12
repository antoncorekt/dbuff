package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class HeroRoleMismatchContributorTest {
  private final HeroRoleMismatchContributor contributor =
      new HeroRoleMismatchContributor(
          new ReasonProperties(),
          new ModeTestKits(
              Map.of(
                  5L, ModeTestKits.hero(5, "Crystal Maiden", "Support", "Disabler", "Nuker"),
                  1L, ModeTestKits.hero(1, "Anti-Mage", "Carry", "Escape"),
                  2L, ModeTestKits.hero(2, "Axe", "Initiator", "Durable", "Disabler"),
                  3L, ModeTestKits.hero(3, "Untagged")),
              Map.of()));

  private static PlayerRoleDomain role(long slot, int role) {
    return PlayerRoleDomain.builder().playerSlot(slot).role(role).build();
  }

  private static FightPlayerDomain row(long slot, String presence) {
    return FightPlayerDomain.builder().playerSlot(slot).presence(presence).build();
  }

  private static FightContext context(List<PlayerRoleDomain> roles, FightPlayerDomain... rows) {
    return new FightContext(
        1,
        TestModes.ALL_PICK,
        null,
        List.of(rows),
        List.of(),
        List.of(),
        roles,
        List.of(),
        List.of(),
        Map.of(0L, 5L, 1L, 1L, 2L, 2L, 3L, 3L, 4L, 5L),
        List.of());
  }

  @Test
  void aHeroWhoseTagsDoNotFitTheRolePlayedIsReportedWithTheRolesSeverity() {
    FightContext context =
        context(
            List.of(role(0, 1), role(1, 5), role(4, 3)),
            row(0, "ACTIVE"),
            row(1, "ACTIVE"),
            row(4, "PRESENT_PASSIVE"));

    List<Reason> reasons = contributor.contribute(context);

    assertThat(reasons)
        .extracting(Reason::code, Reason::subjectSlot, Reason::severity, Reason::source)
        .containsExactly(
            tuple("HERO_ROLE_MISMATCH", 0L, 0.6, "ALL_PICK"),
            tuple("HERO_ROLE_MISMATCH", 1L, 0.3, "ALL_PICK"),
            tuple("HERO_ROLE_MISMATCH", 4L, 0.15, "ALL_PICK"));
    assertThat(reasons.get(0).evidence())
        .isEqualTo("Crystal Maiden (Support/Disabler/Nuker) played carry");
  }

  @Test
  void nothingForAFittingHeroOneOutOfTheFightWithoutTagsOrWithoutAHero() {
    FightContext context =
        context(
            List.of(role(0, 4), role(1, 1), role(2, 3), role(3, 1), role(5, 1)),
            row(0, "ACTIVE"),
            row(1, "ABSENT_FAR"),
            row(2, "ACTIVE"),
            row(3, "ACTIVE"),
            row(5, "ACTIVE"));

    assertThat(contributor.contribute(context)).isEmpty();
  }

  @Test
  void supportsAllPickOnly() {
    assertThat(contributor.supports(TestModes.ALL_PICK)).isTrue();
    assertThat(contributor.supports(TestModes.ABILITY_DRAFT)).isFalse();
  }
}
