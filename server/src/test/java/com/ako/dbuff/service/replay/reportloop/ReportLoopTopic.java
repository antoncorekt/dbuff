package com.ako.dbuff.service.replay.reportloop;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff.Section;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopMatchList;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopTopic;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import com.ako.dbuff.service.replay.report.OpenDotaSection;
import com.ako.dbuff.service.replay.report.ReplayReportProperties;
import com.ako.dbuff.service.replay.report.ReplayReportSection;
import com.ako.dbuff.service.replay.report.ReplayReportService;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import com.ako.dbuff.service.replay.report.SectionSwitch;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.context.ApplicationContext;
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
  private static final Path OUTPUT_DIR = Path.of("build", "report-loop");
  private static final String ROUND_PREFIX = "round-";

  private final ApplicationContext context;
  private final PlainViewRenderer plain;
  private ReplayReportService reportService;
  private Path imageDir;

  ReportLoopTopic(ApplicationContext context, PlainViewRenderer plain) {
    this.context = context;
    this.plain = plain;
  }

  /**
   * The replay test contexts leave the real service out (it needs Discord), so the views are read
   * through one built here from the beans of the context; only what {@code everythingViews} uses is
   * set.
   */
  private synchronized ReplayReportService service() {
    if (reportService == null) {
      reportService =
          context
              .getBeanProvider(ReplayReportService.class)
              .getIfAvailable(
                  () ->
                      new ReplayReportService(
                          context.getBean(ReplayReportProperties.class),
                          context.getBean(ReplayProcessingProperties.class),
                          null,
                          context.getBean(MatchRepo.class),
                          context.getBean(PlayerGameStatisticRepo.class),
                          null,
                          null,
                          null,
                          context
                              .getBeanProvider(ReplayReportSection.class)
                              .orderedStream()
                              .filter(sec -> !OpenDotaSection.NAME.equals(sec.name()))
                              .toList(),
                          context.getBean(SectionSwitch.class),
                          null,
                          null,
                          null,
                          context.getBean(GameModeRegistry.class),
                          null,
                          null,
                          context.getBean(ReportHeroNames.class)));
    }
    return reportService;
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
    return service().everythingSectionNames().stream().map(n -> new Section(n, "", 1)).toList();
  }

  @Override
  public String render(long matchId) {
    return render(
        matchId,
        service().everythingViewsByName(matchId, instance(trackedIds())),
        service().everythingSectionNames(),
        plain,
        roundDir());
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
      long matchId,
      Map<String, List<SectionView>> views,
      List<String> sectionNames,
      PlainViewRenderer plain,
      Path imageDir) {
    List<String> out = new ArrayList<>();
    out.add("# Match " + matchId);
    Set<String> names = new LinkedHashSet<>(sectionNames);
    names.addAll(views.keySet());
    for (String name : names) {
      out.addAll(tableStart(name));
      int imageNo = 0;
      for (SectionView view : views.getOrDefault(name, List.of())) {
        plain.lines(view, true).forEach(l -> out.add(row(l)));
        if (view.image() != null) {
          out.add(
              row("[image " + saveImage(imageDir, matchId, name, view.image(), ++imageNo) + "]"));
        }
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
      Path dir, long matchId, String section, ImageAttachment image, int number) {
    String file = "%d-%s-%d.png".formatted(matchId, section, number);
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
