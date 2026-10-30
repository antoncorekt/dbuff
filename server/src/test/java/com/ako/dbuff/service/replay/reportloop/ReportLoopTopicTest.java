package com.ako.dbuff.service.replay.reportloop;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ReportLoopTopicTest {
  private final PlainViewRenderer plain = new PlainViewRenderer();

  @Test
  void everySectionGetsItsNameAsHeadingAndAnImageIsSavedAndNamed(@TempDir Path dir)
      throws Exception {
    SectionView draft =
        new SectionView(
            "Draft",
            "Ability picks",
            Accent.INFO,
            List.of("A drafted X | pick 5"),
            "note",
            new ImageAttachment("draft.png", new byte[] {1, 2}));
    SectionView fights = new SectionView("Fights", null, Accent.INFO, List.of("3 fights"), null);

    String report =
        ReportLoopTopic.render(
            9L,
            Map.of("draft", List.of(draft), "fights", List.of(fights)),
            List.of("draft", "fights"),
            plain,
            dir);

    assertThat(report)
        .contains("# Match 9", "## draft", "| Ability picks |", "| A drafted X \\| pick 5 |")
        .contains("| note |", "| [image 9-draft-1.png] |", "## fights", "| 3 fights |");
    assertThat(report.indexOf("## draft")).isLessThan(report.indexOf("## fights"));
    assertThat(dir.resolve("9-draft-1.png")).hasBinaryContent(new byte[] {1, 2});
  }

  @Test
  void pagesWithDifferentTitlesSitUnderOneHeading(@TempDir Path dir) {
    SectionView page1 =
        new SectionView("Items (1/2)", null, Accent.INFO, List.of("p1"), null, image());
    SectionView page2 =
        new SectionView("Items (2/2)", null, Accent.INFO, List.of("p2"), null, image());

    String report =
        ReportLoopTopic.render(
            5L, Map.of("items", List.of(page1, page2)), List.of("items"), plain, dir);

    assertThat(report.split("## ", -1)).hasSize(2);
    assertThat(report).contains("[image 5-items-1.png]", "[image 5-items-2.png]");
    assertThat(Files.exists(dir.resolve("5-items-2.png"))).isTrue();
  }

  @Test
  void aRegisteredSectionWithoutViewsStillHasItsHeadingSoRoundsCompare(@TempDir Path dir) {
    String report = ReportLoopTopic.render(1L, Map.of(), List.of("draft", "vision"), plain, dir);

    assertThat(report).contains("## draft\n", "## vision\n");
  }

  @Test
  void theTrackedListIsReadAndBuildsTheInstance() {
    List<Long> ids = ReportLoopTopic.trackedIds();
    DbufInstanceConfigDomain instance = ReportLoopTopic.instance(ids);

    assertThat(ids).containsExactly(204429164L, 279195408L, 208611215L, 201613150L);
    assertThat(instance.getPlayerIds()).containsExactlyInAnyOrderElementsOf(ids);
  }

  private static ImageAttachment image() {
    return new ImageAttachment("x.png", new byte[] {7});
  }
}
