package com.ako.dbuff.service.replay.looprunner;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewLoopDiffTest {
  private static final List<ReviewLoopDiff.Section> SECTIONS =
      List.of(
          new ReviewLoopDiff.Section("Roles", "slot", 1),
          new ReviewLoopDiff.Section("Verdicts", "", 2));

  private static List<String> changes(String previous, String current) {
    return ReviewLoopDiff.changes(previous, current, SECTIONS);
  }

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
    assertThat(changes(report("1", "A"), report("1", "A"))).isEmpty();
  }

  @Test
  void aChangedRoleAndVerdictAreNamed() {
    assertThat(changes(report("1", "A"), report("2", "A,B")))
        .containsExactly("slot 0, final: 1 -> 2", "RADIANT FINAL, tokens: A -> A,B");
  }

  @Test
  void anAddedRowIsReported() {
    String before = report("1", "A").replace("| 5 | Axe | 3 | 3 |\n", "");

    assertThat(changes(before, report("1", "A"))).isEqualTo(List.of("slot 5: added"));
  }

  @Test
  void anUnavailableReportAddsEveryRow() {
    assertThat(changes(ReviewLoopReport.unavailable(1, "x"), report("1", "A")))
        .containsExactly("slot 0: added", "slot 5: added", "RADIANT FINAL: added");
  }

  @Test
  void aSkippedMatchIsNotListedAsRemoved() {
    assertThat(changes(report("1", "A"), ReviewLoopReport.unavailable(1, "no fixture")))
        .containsExactly("skipped this round (no fixture)");
  }

  @Test
  void escapedPipesCrlfAndTrailingBlanksAreRead() {
    String before = report("1", "A").replace("| 0 | Sniper", "| 0 | Sni\\|per");
    String after =
        report("2", "A").replace("| 0 | Sniper", "| 0 | Sni\\|per").replace("\n", "  \r\n");

    assertThat(changes(before, after)).containsExactly("slot 0, final: 1 -> 2");
  }

  @Test
  void aChangedHeaderIsAFormatChange() {
    String after =
        report("1", "A").replace("| slot | hero | intent | final |", "| slot | hero | final |");

    assertThat(changes(report("1", "A"), after))
        .singleElement()
        .asString()
        .startsWith("format changed in Roles");
  }

  @Test
  void aRowWithTheWrongCellCountIsAFormatChange() {
    String after = report("1", "A").replace("| 5 | Axe | 3 | 3 |", "| 5 | Axe | 3 |");

    assertThat(changes(report("1", "A"), after))
        .singleElement()
        .asString()
        .startsWith("format changed in Roles, slot 5");
  }

  @Test
  void columnsAddedAtTheEndDoNotHideTheChangesInTheSharedOnes() {
    String after =
        report("2", "A")
            .replace("| slot | hero | intent | final |", "| slot | hero | intent | final | fit |")
            .replace("|---|---|---|---|\n", "|---|---|---|---|---|\n")
            .replace("| 0 | Sniper | 1 | 2 |", "| 0 | Sniper | 1 | 2 | 1(1.00) |")
            .replace("| 5 | Axe | 3 | 3 |", "| 5 | Axe | 3 | 3 | 3(2.00) |");

    assertThat(changes(report("1", "A"), after)).containsExactly("slot 0, final: 1 -> 2");
  }
}
