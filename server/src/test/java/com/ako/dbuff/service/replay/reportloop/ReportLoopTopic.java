package com.ako.dbuff.service.replay.reportloop;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff.Section;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopMatchList;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopTopic;
import com.ako.dbuff.service.replay.report.ReplayReportService;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The report loop: every view of {@code /match report section:everything} for the focus players,
 * one {@code ## <section>} heading per section and its pictures next to the report. Each body line
 * is a row of a one-column table, so a round comparison names the lines that changed.
 */
@Component
class ReportLoopTopic implements ReviewLoopTopic {
  static final Path MATCH_LIST = Path.of("src/test/resources/replay/report-loop/matches.txt");
  static final Path TRACKED_LIST = Path.of("src/test/resources/replay/report-loop/tracked.txt");
  static final List<String> KNOWN_TITLES =
      List.of(
          "Draft",
          "Roles and composition",
          "Skill picks",
          "Laning stage",
          "Items",
          "Fights",
          "Important fights",
          "Swings",
          "Deaths",
          "Net worth outliers",
          "Team rotations",
          "Vision",
          "Smoke");

  private static final Path OUTPUT_DIR = Path.of("build", "report-loop");
  private static final String ROUND_PREFIX = "round-";

  private final ReplayReportService reportService;
  private final PlainViewRenderer plain;
  private Path imageDir;

  ReportLoopTopic(ReplayReportService reportService, PlainViewRenderer plain) {
    this.reportService = reportService;
    this.plain = plain;
  }

  @Override
  public String name() {
    return "report";
  }

  @Override
  public Path matchList() {
    return MATCH_LIST;
  }

  @Override
  public Path outputDir() {
    return OUTPUT_DIR;
  }

  @Override
  public List<Section> sections() {
    return KNOWN_TITLES.stream().map(t -> new Section(t, "", 1)).toList();
  }

  @Override
  public String render(long matchId) {
    List<SectionView> views = reportService.everythingViews(matchId, instance(trackedIds()));
    return render(matchId, views, plain, roundDir());
  }

  static DbufInstanceConfigDomain instance(List<Long> accountIds) {
    Set<PlayerDomain> players = new LinkedHashSet<>();
    accountIds.forEach(
        id -> players.add(PlayerDomain.builder().id(id).name(String.valueOf(id)).build()));
    return DbufInstanceConfigDomain.builder().name("report-loop").players(players).build();
  }

  static List<Long> trackedIds() {
    return ReviewLoopMatchList.read(TRACKED_LIST);
  }

  static String render(
      long matchId, List<SectionView> views, PlainViewRenderer plain, Path imageDir) {
    List<String> out = new ArrayList<>();
    out.add("# Match " + matchId);
    Set<String> seen = new HashSet<>();
    Map<String, Integer> counts = new HashMap<>();
    int untitled = 0;
    for (SectionView view : views) {
      String heading = view.title().isBlank() ? "Untitled " + (++untitled) : view.title();
      if (seen.add(heading)) {
        out.addAll(tableStart(heading));
      }
      List<String> lines = plain.lines(view, true);
      lines.stream().skip(view.title().isBlank() ? 0 : 1).forEach(l -> out.add(row(l)));
      if (view.image() != null) {
        String file = saveImage(imageDir, matchId, heading, view.image(), counts);
        out.add(row("[image " + file + "]"));
      }
    }
    for (String title : KNOWN_TITLES) {
      if (seen.add(title)) {
        out.addAll(tableStart(title));
      }
    }
    return String.join("\n", out) + "\n";
  }

  private static List<String> tableStart(String heading) {
    return List.of("", "## " + heading, "", "| text |", "|---|");
  }

  private static String row(String text) {
    String cell = text.replace("\\", "\\\\").replace("|", "\\|").replaceAll("\\s*\\R\\s*", " ");
    return "| " + cell.strip() + " |";
  }

  private static String saveImage(
      Path dir, long matchId, String heading, ImageAttachment image, Map<String, Integer> counts) {
    String slug =
        heading.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    String file = "%d-%s-%d.png".formatted(matchId, slug, counts.merge(slug, 1, Integer::sum));
    try {
      Files.createDirectories(dir);
      Files.write(dir.resolve(file), image.bytes());
      return file;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private synchronized Path roundDir() {
    if (imageDir == null) {
      imageDir = OUTPUT_DIR.resolve(ROUND_PREFIX + "%02d".formatted(roundNumber()));
    }
    return imageDir;
  }

  private static int roundNumber() {
    String requested = System.getenv("ROUND");
    if (requested != null && !requested.isBlank()) {
      return Integer.parseInt(requested.strip());
    }
    if (!Files.isDirectory(OUTPUT_DIR)) {
      return 1;
    }
    try (Stream<Path> dirs = Files.list(OUTPUT_DIR)) {
      return IntStream.of(
                  dirs.map(d -> d.getFileName().toString())
                      .filter(n -> n.matches(ROUND_PREFIX + "\\d+"))
                      .mapToInt(n -> Integer.parseInt(n.substring(ROUND_PREFIX.length())))
                      .toArray())
              .max()
              .orElse(0)
          + 1;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
