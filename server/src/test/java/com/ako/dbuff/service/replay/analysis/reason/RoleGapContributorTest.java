package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RoleGapContributorTest {
  private final RoleGapContributor contributor = new RoleGapContributor(new ReasonProperties());

  private static TeamCompositionDomain composition(String team, String stage, String tokens) {
    return TeamCompositionDomain.builder().team(team).stage(stage).tokens(tokens).build();
  }

  private static FightContext context(String winner, TeamCompositionDomain... compositions) {
    return new FightContext(
        1,
        TestModes.ALL_PICK,
        FightDomain.builder().winner(winner).build(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(compositions));
  }

  @Test
  void theLosersFinalMissingFunctionsBecomeTeamReasons() {
    FightContext context =
        context(
            "DIRE",
            composition("RADIANT", "FINAL", "CARRIES_1,NO_INITIATOR,NO_VISION_DUTY,TOP_HEAVY"),
            composition("RADIANT", "PICK", "NO_DISABLE"),
            composition("DIRE", "FINAL", "NO_DISABLE"));

    List<Reason> reasons = contributor.contribute(context);

    assertThat(reasons)
        .extracting(Reason::code, Reason::scope, Reason::team, Reason::severity)
        .containsExactly(
            tuple("NO_INITIATOR", ReasonScope.TEAM, TeamSide.RADIANT, 0.5),
            tuple("NO_VISION_DUTY", ReasonScope.TEAM, TeamSide.RADIANT, 0.2));
    assertThat(reasons.get(0).subjectSlot()).isNull();
    assertThat(reasons.get(0).evidence()).isEqualTo("Radiant, the losing team: no initiator");
  }

  @Test
  void nothingForADrawAnUnknownWinnerOrNoCompositionOrTokens() {
    TeamCompositionDomain radiant = composition("RADIANT", "FINAL", "NO_DISABLE");
    TeamCompositionDomain dire = composition("DIRE", "FINAL", "");
    assertThat(contributor.contribute(context("DRAW", radiant, dire))).isEmpty();
    assertThat(contributor.contribute(context(null, radiant, dire))).isEmpty();
    assertThat(contributor.contribute(context("RADIANT", radiant))).isEmpty();
    assertThat(contributor.contribute(context("RADIANT", radiant, dire))).isEmpty();
    assertThat(contributor.contribute(context("DIRE"))).isEmpty();
  }

  @Test
  void supportsEveryMode() {
    assertThat(contributor.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(contributor.supports(TestModes.ALL_PICK)).isTrue();
  }
}
