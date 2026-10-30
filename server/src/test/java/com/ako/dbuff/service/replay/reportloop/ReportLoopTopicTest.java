package com.ako.dbuff.service.replay.reportloop;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ReportLoopTopicTest {
  private final PlainViewRenderer plain = new PlainViewRenderer();

  @Test
  void everyViewGetsItsHeadingAndTextAndAnImageIsSavedAndNamed(@TempDir Path dir) throws Exception {
    SectionView draft =
        new SectionView(
            "Draft",
            "Ability picks",
            Accent.INFO,
            List.of("A drafted X | pick 5"),
            "note",
            new ImageAttachment("draft.png", new byte[] {1, 2}));
    SectionView fights = new SectionView("Fights", null, Accent.INFO, List.of("3 fights"), null);

    String report = ReportLoopTopic.render(9L, List.of(draft, fights), plain, dir);

    assertThat(report)
        .contains("# Match 9", "## Draft", "| Ability picks |", "| A drafted X \\| pick 5 |")
        .contains("| note |", "| [image 9-draft-1.png] |", "## Fights", "| 3 fights |");
    assertThat(report.indexOf("## Draft")).isLessThan(report.indexOf("## Fights"));
    assertThat(dir.resolve("9-draft-1.png")).hasBinaryContent(new byte[] {1, 2});
  }

  @Test
  void pagesOfOneSectionShareOneHeadingAndNumberTheirImages(@TempDir Path dir) {
    SectionView page1 = new SectionView("Items", null, Accent.INFO, List.of("p1"), null, image());
    SectionView page2 = new SectionView("Items", null, Accent.INFO, List.of("p2"), null, image());

    String report = ReportLoopTopic.render(5L, List.of(page1, page2), plain, dir);

    assertThat(report.split("## Items", -1)).hasSize(2);
    assertThat(report).contains("[image 5-items-1.png]", "[image 5-items-2.png]");
    assertThat(Files.exists(dir.resolve("5-items-2.png"))).isTrue();
  }

  @Test
  void aSectionWithoutViewsStillHasItsHeadingSoRoundsCompare(@TempDir Path dir) {
    String report = ReportLoopTopic.render(1L, List.of(), plain, dir);

    ReportLoopTopic.KNOWN_TITLES.forEach(t -> assertThat(report).contains("## " + t + "\n"));
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
