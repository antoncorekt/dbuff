package com.ako.dbuff.service.replay.roleloop;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleLoopDiffTest {
  private static String report(String slot0Final, String tokens) {
    return """
    # Match 1

    ## Roles

    | slot | hero | intent | final |
    |---|---|---|---|
    | 0 | Sniper | 1 | %s |
    | 5 | Axe | 3 | 3 |

    ## Verdicts

    | team | stage | tokens |
    |---|---|---|
    | RADIANT | FINAL | %s |

    ## Players
    | 0 | not a table of interest | x | y |
    """
        .formatted(slot0Final, tokens);
  }

  @Test
  void identicalReportsHaveNoChanges() {
    assertThat(RoleLoopDiff.changes(report("1", "A"), report("1", "A"))).isEmpty();
  }

  @Test
  void aChangedRoleAndVerdictAreNamed() {
    assertThat(RoleLoopDiff.changes(report("1", "A"), report("2", "A,B")))
        .containsExactly("slot 0, final: 1 -> 2", "RADIANT FINAL, tokens: A -> A,B");
  }

  @Test
  void anAddedRowIsReported() {
    String before = report("1", "A").replace("| 5 | Axe | 3 | 3 |\n", "");

    assertThat(RoleLoopDiff.changes(before, report("1", "A"))).isEqualTo(List.of("slot 5: added"));
  }

  @Test
  void anUnavailableReportAddsEveryRow() {
    assertThat(RoleLoopDiff.changes(RoleLoopReport.unavailable(1, "x"), report("1", "A")))
        .containsExactly("slot 0: added", "slot 5: added", "RADIANT FINAL: added");
  }
}
