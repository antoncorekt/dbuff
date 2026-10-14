package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.ReplayRunService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Processes the replays of {@code replay/load-test/matches.txt} one after another through the whole
 * pipeline against a real PostgreSQL, as {@link ReplayPostgresSmokeTest} does, and records time,
 * heap and stored rows per replay. It runs under the 512 MB heap of {@code :server:replayTest}. The
 * table goes to the console and to {@code send/load-test.md} next to {@code REPLAY_FIXTURE_DIR}.
 */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "SMOKE_PG_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@ReplayPipelineTest
class ReplayLoadTest extends ReplayPipelineFixture {
  private static final String MATCH_LIST = "/replay/load-test/matches.txt";
  private static final Map<Long, String> MODES = Map.of(18L, "Ability Draft", 22L, "All Pick");
  private static final long MB = 1024 * 1024;

  /**
   * The live heap after the last run may exceed the one after the first by this many MB. In the
   * 20-replay run of 2026-10-14 it stayed within 3 MB, so 128 MB is generous and only a real leak
   * trips it; {@code LOAD_TEST_HEAP_GROWTH_MB} overrides it.
   */
  private static final long MAX_HEAP_GROWTH_MB = growthLimit();

  /**
   * Ceiling for the old generation peak of one run under the 512 MB heap. The 20-replay run peaked
   * at 161 MB (a 76-minute game), so 400 MB leaves room for a longer game and for garbage that the
   * previous run left in the pools, while staying below the heap that would end in an
   * OutOfMemoryError.
   */
  private static final long MAX_OLD_GEN_PEAK_MB = 400;

  @Autowired private ReplayRunService runService;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ReplayProcessingProperties properties;

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void everyReplayIsProcessedOneAfterAnotherWithoutFailingOrRunningOutOfMemory() throws Exception {
    List<String> ids = matchIds();
    assertThat(ids.stream().filter(id -> !hasFixture(id)))
        .as("matches missing from REPLAY_FIXTURE_DIR")
        .isEmpty();
    assertThat(runRepo.count())
        .as(
            "replay_parse_run is not empty: start a fresh postgres container (see"
                + " docs/replay-fixtures.md)")
        .isZero();
    HeapProbe heap = new HeapProbe();
    long baseline = HeapProbe.liveMbAfterGc();
    List<LoadTestRun> runs = new ArrayList<>();
    Map<String, List<Long>> rowsPerTable = new HashMap<>();
    long started = System.nanoTime();

    for (String id : ids) {
      runs.add(processOne(id, heap, rowsPerTable));
    }

    LoadTestReport report =
        new LoadTestReport(
            runs,
            baseline,
            Runtime.getRuntime().maxMemory() / MB,
            (System.nanoTime() - started) / 1e9,
            rowsPerTable);
    String text = report.render();
    System.out.println(text);
    writeReport(text);

    assertThat(runs).hasSize(ids.size());
    assertThat(runs.stream().filter(run -> !run.passed()))
        .as("runs that did not end DONE (a PARTIAL one is a failure, its note says why)")
        .isEmpty();
    assertThat(runs.stream().filter(run -> run.rows() <= 0))
        .as("runs with no stored rows")
        .isEmpty();
    assertThat(runs.getLast().liveMb())
        .as("live heap after the last run against the first")
        .isLessThanOrEqualTo(runs.getFirst().liveMb() + MAX_HEAP_GROWTH_MB);
    assertThat(runs.stream().filter(run -> run.peakOldMb() > MAX_OLD_GEN_PEAK_MB))
        .as("runs whose old generation peak passed %d MB", MAX_OLD_GEN_PEAK_MB)
        .isEmpty();
  }

  private static void writeReport(String text) {
    try {
      Path target = fixture("").toAbsolutePath().resolveSibling("send").resolve("load-test.md");
      Files.createDirectories(target.getParent());
      Files.writeString(target, text);
    } catch (IOException | RuntimeException e) {
      System.out.println("The load test table was not written to a file: " + e);
    }
  }

  private static long growthLimit() {
    String value = System.getenv("LOAD_TEST_HEAP_GROWTH_MB");
    return value == null || value.isBlank() ? 128 : Long.parseLong(value.trim());
  }

  private LoadTestRun processOne(String id, HeapProbe heap, Map<String, List<Long>> rowsPerTable)
      throws IOException {
    JsonNode json = new ObjectMapper().readTree(fixture(id + ".json").toFile());
    MatchDomain match = seedMatch(id);
    heap.resetPeak();
    long begin = System.nanoTime();
    String status;
    String note = "";
    try {
      ReplayStatus result = runService.process(match);
      status = result.name();
      if (result != ReplayStatus.DONE) {
        note =
            runRepo
                .findByMatchIdAndParserVersion(match.getId(), properties.getParserVersion())
                .map(ReplayParseRunDomain::getError)
                .orElse("");
      }
    } catch (Exception t) {
      status = "CRASH";
      note = t.getClass().getName() + ": " + t.getMessage();
    }
    double seconds = (System.nanoTime() - begin) / 1e9;
    long peak = heap.peakMb();
    long peakOld = heap.peakOldMb();
    long live = HeapProbe.liveMbAfterGc();
    long rows = countRows(match.getId(), rowsPerTable);
    return new LoadTestRun(
        match.getId(),
        MODES.getOrDefault(json.path("game_mode").asLong(), "mode " + json.path("game_mode")),
        Math.round(json.path("duration").asLong() / 60.0),
        seconds,
        live,
        peak,
        peakOld,
        status,
        rows,
        note == null ? "" : note);
  }

  private long countRows(long matchId, Map<String, List<Long>> rowsPerTable) {
    long total = 0;
    for (String table : tablesWithMatchId()) {
      Long count =
          jdbc.queryForObject(
              "select count(*) from \"" + table + "\" where match_id = ?", Long.class, matchId);
      if (count != null && count > 0) {
        rowsPerTable.computeIfAbsent(table, t -> new ArrayList<>()).add(count);
        total += count;
      }
    }
    return total;
  }

  private List<String> tablesWithMatchId() {
    return jdbc.queryForList(
        "select table_name from information_schema.columns"
            + " where column_name = 'match_id' and table_schema = 'public' order by table_name",
        String.class);
  }

  private static List<String> matchIds() throws IOException {
    try (InputStream in = ReplayLoadTest.class.getResourceAsStream(MATCH_LIST)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .map(line -> line.replaceFirst("#.*", "").trim())
          .filter(line -> !line.isEmpty())
          .toList();
    }
  }
}
