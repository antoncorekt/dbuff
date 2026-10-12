package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.dao.repo.GameStateSegmentRepo;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerPhaseSummaryRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerMinuteRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.service.replay.AbilityNames;
import com.ako.dbuff.service.replay.ReplayDataService;
import com.ako.dbuff.service.replay.ReplayRunService;
import com.ako.dbuff.service.replay.report.DeathSection;
import com.ako.dbuff.service.replay.report.DraftSection;
import com.ako.dbuff.service.replay.report.FightSection;
import com.ako.dbuff.service.replay.report.MovementSection;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.ako.dbuff.service.replay.report.SwingSection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a real replay through the whole pipeline against a real PostgreSQL: a local HTTP server
 * stands in for Valve, the real downloader, decompressor, parser, extractors and analyzers run, and
 * the tables are created by Hibernate as in production. It needs a PostgreSQL URL in {@code
 * SMOKE_PG_URL} (user postgres, password password) and the replays and OpenDota's JSON of them in
 * {@code REPLAY_FIXTURE_DIR}, as {@code <id>.dem.zst} and {@code <id>.json}. OpenDota's gold stands
 * in for the net worth timeline, so the analyzers that read it run too.
 */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "SMOKE_PG_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@DataJpaTest(
    properties = {
      "spring.datasource.url=${SMOKE_PG_URL}",
      "spring.datasource.username=postgres",
      "spring.datasource.password=password",
      "spring.datasource.driver-class-name=org.postgresql.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.jpa.hibernate.ddl-auto=update",
      "spring.jpa.show-sql=false",
      "replay.processing.max-attempts=1"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ComponentScan(
    basePackages = "com.ako.dbuff.service.replay",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern =
                ".*\\.(ReplayProcessingJob|ReplayRunJanitor|ReplayQueue|ReplayBatchService|ConstantAbilityNames|ReplayTempCleaner|ReplayReportService)|.*Test(\\$.*)?"))
@Import({
  ReplayPostgresSmokeTest.Config.class,
  SwingSection.class,
  DeathSection.class,
  FightSection.class,
  MovementSection.class,
  DraftSection.class
})
@TestPropertySource(properties = "replay.processing.enabled=true")
class ReplayPostgresSmokeTest {

  /** Net worth is never zero, as gold is at the first minute; this stands in for the items. */
  private static final long STARTING_WORTH = 4000;

  private static final String[] MATCHES = {"9040889566", "9040892342"};
  private static HttpServer server;

  @DynamicPropertySource
  static void replayServer(DynamicPropertyRegistry registry) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String file = Path.of(exchange.getRequestURI().getPath()).getFileName().toString();
          String match = file.substring(0, file.indexOf('_'));
          byte[] body =
              Files.readAllBytes(Path.of(System.getenv("REPLAY_FIXTURE_DIR"), match + ".dem.zst"));
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    registry.add(
        "replay.processing.url-template",
        () -> "http://localhost:" + server.getAddress().getPort() + "/replay%d/570/%d_%d.dem.bz2");
    registry.add(
        "replay.processing.temp-dir", () -> System.getProperty("java.io.tmpdir") + "/smoke");
  }

  @AfterAll
  static void stop() throws IOException {
    server.stop(0);
    Path dir = Path.of(System.getProperty("java.io.tmpdir"), "smoke");
    if (Files.isDirectory(dir)) {
      try (var files = Files.list(dir)) {
        files.forEach(f -> f.toFile().delete());
      }
    }
  }

  @TestConfiguration
  static class Config {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    AbilityNames abilityNames() {
      return id -> Optional.of("ability_" + id);
    }
  }

  @Autowired private ReplayRunService runService;
  @Autowired private ReplayDataService dataService;
  @Autowired private PlayerGameStatisticRepo playerStatRepo;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private ReplayPlayerMinuteRepo minuteRepo;
  @Autowired private ReplayStateChangeRepo stateRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private PlayerActivitySegmentRepo activityRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private DeathAnalysisRepo deathRepo;
  @Autowired private BuybackEventRepo buybackRepo;
  @Autowired private PlayerPhaseSummaryRepo phaseRepo;
  @Autowired private GameSpikeRepo spikeRepo;
  @Autowired private GameStateSegmentRepo stateSegmentRepo;
  @Autowired private PlayerTimelineRepo timelineRepo;
  @Autowired private SwingSection swingSection;
  @Autowired private DeathSection deathSection;
  @Autowired private FightSection fightSection;
  @Autowired private MovementSection movementSection;
  @Autowired private DraftSection draftSection;

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void aRealReplayGoesThroughEveryStageAndLandsInPostgres() throws Exception {
    Assumptions.assumeTrue(
        System.getenv("SMOKE_PG_URL").contains("localhost")
            || System.getenv("SMOKE_PG_URL").contains("127.0.0.1"),
        "The smoke test changes the schema and commits data, so it only runs against localhost");
    for (String id : MATCHES) {
      MatchDomain match = match(id);

      ReplayStatus status = runService.process(match);

      ReplayParseRunDomain run =
          runRepo.findByMatchIdAndParserVersion(match.getId(), 1).orElseThrow();
      assertThat(status).as("%s: %s", id, run.getError()).isEqualTo(ReplayStatus.DONE);
      assertThat(playerRepo.findByMatchId(match.getId())).hasSize(10);
      assertThat(trackRepo.findByMatchId(match.getId())).hasSize(10);
      assertThat(trackRepo.findByMatchId(match.getId()).getFirst().getMana()).isNotEmpty();
      assertThat(minuteRepo.findByMatchId(match.getId())).isNotEmpty();
      assertThat(stateRepo.findByMatchIdOrderByGameSeconds(match.getId())).isNotEmpty();
      assertThat(combatLogRepo.findAll().stream().filter(c -> c.getMatchId().equals(match.getId())))
          .hasSize(1);
      assertThat(activityRepo.findByMatchId(match.getId())).isNotEmpty();
      assertThat(fightRepo.findByMatchId(match.getId())).isNotEmpty();
      assertThat(deathRepo.findByMatchId(match.getId())).hasSizeGreaterThan(40);
      assertThat(phaseRepo.findByMatchId(match.getId())).hasSizeGreaterThanOrEqualTo(20);
      assertThat(buybackRepo.findByMatchId(match.getId())).isNotEmpty();
      assertThat(spikeRepo.findByMatchId(match.getId())).isNotEmpty();
      assertThat(stateSegmentRepo.findByMatchId(match.getId())).isNotEmpty();
      ReplayReportInput input =
          new ReplayReportInput(
              match.getId(),
              match,
              java.util.Set.of(0L, 1L, 2L, 3L, 4L, 128L, 129L, 130L, 131L, 132L),
              java.util.Map.of(
                  0L, "P0", 1L, "P1", 2L, "P2", 3L, "P3", 4L, "P4", 128L, "P5", 129L, "P6", 130L,
                  "P7", 131L, "P8", 132L, "P9"),
              3);
      java.util.List<String> report = new java.util.ArrayList<>();
      report.addAll(swingSection.lines(input));
      report.addAll(deathSection.lines(input));
      report.addAll(fightSection.lines(input));
      report.addAll(movementSection.lines(input));
      report.addAll(draftSection.lines(input));
      System.out.println("REPORT " + id + "\n" + String.join("\n", report));
      assertThat(report).isNotEmpty();
    }
    ReplayStatus again = runService.process(match(MATCHES[0]));
    assertThat(again).as("a second run replaces the first").isEqualTo(ReplayStatus.DONE);
    assertThat(deathRepo.findByMatchId(Long.parseLong(MATCHES[0]))).hasSizeGreaterThan(40);
    dataService.deleteByMatchId(Long.parseLong(MATCHES[0]));
    assertThat(deathRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
  }

  private MatchDomain match(String id) throws IOException {
    JsonNode json =
        new ObjectMapper()
            .readTree(Path.of(System.getenv("REPLAY_FIXTURE_DIR"), id + ".json").toFile());
    MatchDomain match = new MatchDomain();
    match.setId(Long.parseLong(id));
    match.setReplayCluster(json.get("cluster").asLong());
    match.setReplaySalt(json.get("replay_salt").asLong());
    match.setGameModeId(json.get("game_mode").asLong());
    match.setDuration(json.get("duration").asLong());
    match.setStartTime(json.get("start_time").asLong());
    playerStatRepo.deleteAll(playerStatRepo.findAllByMatchId(match.getId()));
    timelineRepo.deleteByMatchId(match.getId());
    for (JsonNode p : json.get("players")) {
      long slot = p.get("player_slot").asLong();
      for (int minute = 0; minute < p.get("gold_t").size(); minute++) {
        long gold = p.get("gold_t").get(minute).asLong();
        timelineRepo.save(
            PlayerTimelineDomain.builder()
                .matchId(match.getId())
                .playerSlot(slot)
                .gameMinute(minute)
                .gold(gold)
                .xp(p.get("xp_t").get(minute).asLong())
                .networth(gold + STARTING_WORTH)
                .build());
      }
      PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
      stat.setMatchId(match.getId());
      stat.setPlayerSlot(p.get("player_slot").asLong());
      stat.setPlayerId(p.get("account_id").asLong());
      playerStatRepo.save(stat);
    }
    return match;
  }
}
