package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

class WeakCombinationContributorTest {
  private final WeakCombinationContributor contributor =
      new WeakCombinationContributor(new ReasonProperties());

  private static PlayerRoleProfileDomain profile(
      long slot, String stage, Double initiation, Double disable, Double damage) {
    return PlayerRoleProfileDomain.builder()
        .playerSlot(slot)
        .stage(stage)
        .initiation(initiation)
        .disable(disable)
        .damage(damage)
        .build();
  }

  private static FightContext context(String winner, PlayerRoleProfileDomain... profiles) {
    return new FightContext(
        1,
        TestModes.ABILITY_DRAFT,
        FightDomain.builder().winner(winner).build(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(profiles),
        Map.of(),
        List.of());
  }

  @Test
  void theLosersBestPlayerStayingLowInTwoFunctionsIsWeak_andEachMoreRaisesTheSeverity() {
    List<PlayerRoleProfileDomain> two =
        List.of(
            profile(0, "INTENT", 0.5, 0.2, 0.9),
            profile(1, "INTENT", 0.3, 0.59, 0.8),
            profile(128, "INTENT", 0.0, 0.0, 0.0));
    List<PlayerRoleProfileDomain> three =
        List.of(profile(0, "INTENT", 0.5, 0.2, 0.3), profile(1, "INTENT", 0.3, 0.4, 0.59));

    List<Reason> weak =
        contributor.contribute(context("DIRE", two.toArray(new PlayerRoleProfileDomain[0])));

    assertThat(weak)
        .extracting(Reason::code, Reason::scope, Reason::team, Reason::severity, Reason::source)
        .containsExactly(
            tuple("WEAK_COMBINATION", ReasonScope.TEAM, TeamSide.RADIANT, 0.4, "ABILITY_DRAFT"));
    assertThat(weak.getFirst().subjectSlot()).isNull();
    assertThat(weak.getFirst().evidence())
        .isEqualTo(
            "Radiant drafted skills whose best player reaches only: initiation 0.50, disable"
                + " 0.59");
    assertThat(
            contributor
                .contribute(context("DIRE", three.toArray(new PlayerRoleProfileDomain[0])))
                .getFirst()
                .severity())
        .isCloseTo(0.6, within(1e-9));
  }

  @Test
  void nothingForOneLowFunctionTheWinnerTheFinalStageUnknownValuesADrawOrNoProfiles() {
    assertThat(
            contributor.contribute(
                context(
                    "DIRE",
                    profile(0, "INTENT", 0.5, 0.9, 0.9),
                    profile(1, "INTENT", 0.3, 0.9, 0.9))))
        .isEmpty();
    assertThat(contributor.contribute(context("RADIANT", profile(0, "INTENT", 0.1, 0.1, 0.1))))
        .isEmpty();
    assertThat(contributor.contribute(context("DIRE", profile(0, "FINAL", 0.1, 0.1, 0.1))))
        .isEmpty();
    assertThat(contributor.contribute(context("DIRE", profile(0, "INTENT", null, null, 0.1))))
        .isEmpty();
    assertThat(contributor.contribute(context("DRAW", profile(0, "INTENT", 0.1, 0.1, 0.1))))
        .isEmpty();
    assertThat(contributor.contribute(context("DIRE"))).isEmpty();
  }

  @Test
  void supportsAbilityDraftOnly() {
    assertThat(contributor.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(contributor.supports(TestModes.ALL_PICK)).isFalse();
  }
}
