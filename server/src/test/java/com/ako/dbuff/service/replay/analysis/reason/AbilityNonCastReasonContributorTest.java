package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AbilityNonCastReasonContributorTest {
  private final AbilityNonCastReasonContributor contributor =
      new AbilityNonCastReasonContributor(new ReasonProperties());

  private static FightAbilityUseDomain use(long slot, String name, String kind, String reason) {
    return FightAbilityUseDomain.builder()
        .playerSlot(slot)
        .abilityName(name)
        .kind(kind)
        .nonCastReason(reason)
        .build();
  }

  private static FightContext context(String presence, FightAbilityUseDomain... uses) {
    return new FightContext(
        1,
        TestModes.ALL_PICK,
        null,
        List.of(FightPlayerDomain.builder().playerSlot(0L).presence(presence).build()),
        List.of(uses),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Map.of(),
        List.of());
  }

  @Test
  void cooldownsAndMissingManaGetTheirCodes_andItemsAreToldFromAbilities() {
    List<Reason> reasons =
        contributor.contribute(
            context(
                "ACTIVE",
                use(0, "sven_storm_bolt", "ABILITY", "ON_COOLDOWN"),
                use(0, "item_blink", "ITEM", "ON_COOLDOWN"),
                use(0, "sven_gods_strength", "ABILITY", "NO_MANA"),
                use(0, "sven_warcry", "ABILITY", "READY_NOT_CAST"),
                use(0, "sven_great_cleave", "ABILITY", "DEAD_EARLY"),
                use(0, "item_bkb", "ITEM", null)));

    assertThat(reasons)
        .extracting(Reason::code)
        .containsExactlyInAnyOrder("ABILITY_ON_COOLDOWN", "ITEM_ON_COOLDOWN", "NO_MANA");
    assertThat(reasons)
        .filteredOn(r -> r.code().equals("NO_MANA"))
        .singleElement()
        .satisfies(r -> assertThat(r.severity()).isEqualTo(0.5));
  }

  @Test
  void severalAbilitiesOfOneHeroAndCodeMakeOneReasonThatListsThemAndWeighsMore() {
    List<Reason> reasons =
        contributor.contribute(
            context(
                "ACTIVE",
                use(0, "a", "ABILITY", "ON_COOLDOWN"),
                use(0, "b", "ABILITY", "ON_COOLDOWN")));

    assertThat(reasons)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.evidence()).isEqualTo("a, b");
              assertThat(r.severity()).isCloseTo(0.5, within(1e-9));
            });
  }

  @Test
  void aHeroWhoOnlyStoodNearTheFightWeighsLess() {
    List<Reason> reasons =
        contributor.contribute(context("PRESENT_PASSIVE", use(0, "a", "ABILITY", "ON_COOLDOWN")));

    assertThat(reasons.getFirst().severity()).isCloseTo(0.2, within(1e-9));
  }

  @Test
  void aRepeatedAbilityCountsOnce_aRowWithoutPlayerOrWithAnUnknownReasonIsSkipped() {
    List<Reason> reasons =
        contributor.contribute(
            context(
                "ACTIVE",
                use(0, "a", "ABILITY", "ON_COOLDOWN"),
                use(0, "a", "ABILITY", "ON_COOLDOWN"),
                FightAbilityUseDomain.builder()
                    .abilityName("x")
                    .kind("ABILITY")
                    .nonCastReason("ON_COOLDOWN")
                    .build(),
                use(0, "y", "ABILITY", "SOMETHING_NEW")));

    assertThat(reasons)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.evidence()).isEqualTo("a");
              assertThat(r.severity()).isCloseTo(0.4, within(1e-9));
            });
  }
}
