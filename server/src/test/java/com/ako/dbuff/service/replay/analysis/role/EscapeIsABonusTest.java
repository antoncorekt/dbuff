package com.ako.dbuff.service.replay.analysis.role;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EscapeIsABonusTest {
  private final RoleFitter fitter = new RoleFitter(new RoleProfileProperties());

  private static FunctionProfile profile(Double escape) {
    return new FunctionProfile(null, 0.8, 0.0, 0.1, null, 0.3, 0.6, 0.2, 0.7, 0.4, escape);
  }

  @Test
  void escapeIsNotInTheScoredFunctions() {
    assertThat(RoleFunction.scoredFunctions())
        .doesNotContain(RoleFunction.ESCAPE)
        .hasSize(RoleFunction.values().length - 1);
    assertThat(RoleFunction.ESCAPE.words()).isEqualTo("escape");
  }

  @Test
  void aProfilesScoresAndConfidenceDoNotDependOnItsEscape() {
    for (RoleStage stage : RoleStage.values()) {
      assertThat(fitter.scores(profile(1.0), stage))
          .containsExactly(fitter.scores(profile(null), stage));
    }
    List<FunctionProfile> team =
        List.of(profile(0.0), profile(1.0), profile(0.5), profile(null), profile(0.2));
    List<FunctionProfile> withoutEscape = team.stream().map(p -> profile(null)).toList();

    assertThat(fitter.fit(team, RoleStage.INTENT).stream().map(RoleFitter.RoleFit::confidence))
        .containsExactlyElementsOf(
            fitter.fit(withoutEscape, RoleStage.INTENT).stream()
                .map(RoleFitter.RoleFit::confidence)
                .toList());
  }
}
