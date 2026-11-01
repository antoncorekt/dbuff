package com.ako.dbuff.service.replay.reportloop;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.service.replay.report.ReplayReportService;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

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
  void theDefaultFileListsSectionsInOrderAndEndsWithTheMessage(@TempDir Path dir) {
    SectionView draft = new SectionView("Draft", null, Accent.INFO, List.of("d1"), null, image());
    SectionView fights = new SectionView("Fights", null, Accent.INFO, List.of("f1"), null);
    Map<String, List<SectionView>> views = new java.util.LinkedHashMap<>();
    views.put("fights", List.of(fights));
    views.put("draft", List.of(draft));

    String report =
        ReportLoopTopic.renderDefault(
            4L,
            new ReplayReportService.DefaultReport(views, Optional.of("More: /match report")),
            plain,
            dir);

    assertThat(report).contains("Messages: 3", "| f1 |", "| d1 |", "[image 4-default-draft-1.png]");
    assertThat(report.indexOf("## fights")).isLessThan(report.indexOf("## draft"));
    assertThat(report.stripTrailing()).endsWith("More: /match report");
    assertThat(Files.exists(dir.resolve("4-default-draft-1.png"))).isTrue();
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
  void aSectionWithoutContentWritesNothing(@TempDir Path dir) {
    String report = ReportLoopTopic.render(1L, Map.of(), List.of("draft", "vision"), plain, dir);

    assertThat(report).doesNotContain("## draft", "## vision").contains("# Match 1");
  }

  @Test
  void theTrackedFileGivesIdsAndNames() {
    Map<Long, String> players = ReportLoopTopic.trackedPlayers();
    DbufInstanceConfigDomain instance = ReportLoopTopic.instance(players);

    assertThat(players)
        .containsExactly(
            entry(204429164L, "Пастух лолей"),
            entry(279195408L, "Доктор Сливси"),
            entry(208611215L, "Лолец пастухов"),
            entry(201613150L, "Невротик"));
    assertThat(instance.getPlayerIds()).containsExactlyInAnyOrderElementsOf(players.keySet());
    assertThat(instance.getPlayers()).extracting(PlayerDomain::getName).contains("Невротик");
  }

  @Test
  void aOneColumnFileStillLoadsWithTheIdAsName() {
    assertThat(ReportLoopTopic.parseTracked(List.of("12", "# note", "", "13 Two Words # c")))
        .containsExactly(entry(12L, "12"), entry(13L, "Two Words"));
  }

  private static ImageAttachment image() {
    return new ImageAttachment("x.png", new byte[] {7});
  }
}
