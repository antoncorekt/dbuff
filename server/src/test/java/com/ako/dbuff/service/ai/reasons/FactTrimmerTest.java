package com.ako.dbuff.service.ai.reasons;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FactTrimmerTest {
  private final FactTrimmer trimmer = new FactTrimmer(ReasonsFixtures.openAi());

  private static Fact fact(String id, FactKind kind, int chars) {
    return new Fact(id, kind, "x".repeat(chars));
  }

  @Test
  void keepsEverythingWithinTheBudget_inPriorityOrder() {
    List<Fact> facts =
        List.of(
            fact("SMOKE_1", FactKind.SMOKE, 20),
            fact("RESULT", FactKind.RESULT, 20),
            fact("F1", FactKind.FIGHT, 20));

    FactTrimmer.Trimmed trimmed = trimmer.trim(facts, 1000);

    assertThat(trimmed.dropped()).isZero();
    assertThat(trimmed.kept()).extracting(Fact::id).containsExactly("RESULT", "F1", "SMOKE_1");
  }

  @Test
  void dropsTheLowestWeightFirst_andTheLaterOfEqualWeights() {
    List<Fact> facts =
        List.of(
            fact("RESULT", FactKind.RESULT, 80),
            fact("F1", FactKind.FIGHT, 80),
            fact("LANE_1", FactKind.LANE, 80),
            fact("SMOKE_1", FactKind.SMOKE, 80),
            fact("ROTATION_1", FactKind.ROTATION, 80),
            fact("ROTATION_2", FactKind.ROTATION, 80));

    FactTrimmer.Trimmed trimmed = trimmer.trim(facts, 100);

    assertThat(trimmed.dropped()).isEqualTo(2);
    assertThat(trimmed.kept())
        .extracting(Fact::id)
        .containsExactly("RESULT", "F1", "LANE_1", "ROTATION_1");
  }

  @Test
  void keepsTheResultEvenWhenTheBudgetIsTiny_orDropsEverythingInOrder() {
    List<Fact> facts =
        List.of(fact("ROTATION_1", FactKind.ROTATION, 400), fact("RESULT", FactKind.RESULT, 40));

    FactTrimmer.Trimmed trimmed = trimmer.trim(facts, 20);

    assertThat(trimmed.kept()).extracting(Fact::id).containsExactly("RESULT");
    assertThat(trimmed.dropped()).isEqualTo(1);
  }

  @Test
  void anEmptyListTrimsToNothing() {
    FactTrimmer.Trimmed trimmed = trimmer.trim(List.of(), 10);

    assertThat(trimmed.kept()).isEmpty();
    assertThat(trimmed.dropped()).isZero();
  }
}
