package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.replay.analysis.role.IntentProfileCalculator;
import com.ako.dbuff.service.replay.analysis.role.RoleOverrides;
import com.ako.dbuff.service.replay.analysis.role.RoleProfileProperties;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class StrongSkillLeftContributorTest {
  private static final Map<String, AbilityConstant> ABILITIES = ModeTestKits.fixtureAbilities();

  private static final ReasonProperties ENABLED = enabled();

  private final StrongSkillLeftContributor contributor =
      new StrongSkillLeftContributor(
          ENABLED,
          new ModeTestKits(Map.of(), ABILITIES),
          new IntentProfileCalculator(new RoleProfileProperties(), RoleOverrides.NONE));

  private static ReasonProperties enabled() {
    ReasonProperties properties = new ReasonProperties();
    properties.getStrongSkillLeft().setEnabled(true);
    return properties;
  }

  private static AbilityDraftPoolDomain pool(int index, String name, Long pickedBy) {
    return AbilityDraftPoolDomain.builder()
        .poolIndex(index)
        .abilityName(name)
        .pickedBySlot(pickedBy)
        .build();
  }

  private static FightContext context(String winner, AbilityDraftPoolDomain... pool) {
    return new FightContext(
        1,
        TestModes.ABILITY_DRAFT,
        FightDomain.builder().winner(winner).build(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Map.of(),
        List.of(pool));
  }

  @Test
  void strongSkillsLeftInThePoolAreOneTeamReasonOfTheLoserAndTheMoreTheMoreSevere() {
    FightContext context =
        context(
            "RADIANT",
            pool(7, "axe_counter_helix", null),
            pool(2, "sven_storm_bolt", null),
            pool(3, "shadow_shaman_fowl_play", null),
            pool(4, "omniknight_purification", 3L),
            pool(5, "no_such_skill", null),
            pool(6, null, null));

    List<Reason> reasons = contributor.contribute(context);

    assertThat(reasons)
        .extracting(Reason::code, Reason::scope, Reason::team, Reason::severity, Reason::source)
        .containsExactly(
            tuple("STRONG_SKILL_LEFT", ReasonScope.TEAM, TeamSide.DIRE, 0.4, "ABILITY_DRAFT"));
    assertThat(reasons.getFirst().evidence())
        .isEqualTo(
            "strong skills nobody drafted: sven_storm_bolt (initiation), axe_counter_helix"
                + " (damage)");
  }

  @Test
  void nothingWhenNoSkippedSkillIsStrongOrTheFightHasNoLoser() {
    assertThat(
            contributor.contribute(
                context("DIRE", pool(1, "shadow_shaman_fowl_play", null), pool(2, "x", null))))
        .isEmpty();
    assertThat(contributor.contribute(context("DRAW", pool(1, "sven_storm_bolt", null)))).isEmpty();
    assertThat(contributor.contribute(context("DIRE"))).isEmpty();
  }

  @Test
  void supportsAbilityDraftOnly() {
    assertThat(contributor.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(contributor.supports(TestModes.ALL_PICK)).isFalse();
  }

  @Test
  void quietUnlessEnabled() {
    StrongSkillLeftContributor off =
        new StrongSkillLeftContributor(
            new ReasonProperties(),
            new ModeTestKits(Map.of(), ABILITIES),
            new IntentProfileCalculator(new RoleProfileProperties(), RoleOverrides.NONE));

    assertThat(off.contribute(context("DIRE", pool(1, "sven_storm_bolt", null)))).isEmpty();
  }
}
