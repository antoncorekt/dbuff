package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.dao.repo.GameStateSegmentRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerPhaseSummaryRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerMinuteRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.AbilityNames;
import com.ako.dbuff.service.replay.ReplayDataService;
import com.ako.dbuff.service.replay.ReplayRunService;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.report.CompositionSection;
import com.ako.dbuff.service.replay.report.DeathSection;
import com.ako.dbuff.service.replay.report.DraftSection;
import com.ako.dbuff.service.replay.report.FightSection;
import com.ako.dbuff.service.replay.report.MovementSection;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.ako.dbuff.service.replay.report.SwingSection;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                ".*\\.(ReplayProcessingJob|ReplayRunJanitor|ReplayQueue|ReplayBatchService|ConstantAbilityNames|ConstantHeroKits|ReplayTempCleaner|ReplayReportService)|.*Test(\\$.*)?"))
@Import({
  ReplayPostgresSmokeTest.Config.class,
  SwingSection.class,
  DeathSection.class,
  FightSection.class,
  MovementSection.class,
  DraftSection.class,
  CompositionSection.class
})
@TestPropertySource(properties = "replay.processing.enabled=true")
class ReplayPostgresSmokeTest {

  /** Net worth is never zero, as gold is at the first minute; this stands in for the items. */
  private static final long STARTING_WORTH = 4000;

  private static final String[] MATCHES = {"9040889566", "9040892342"};
  private static final String[] ABILITY_DRAFT_MATCHES = {"9038725154", "9037038717"};
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
    AbilityNames abilityNames() throws IOException {
      Map<String, String> names = new HashMap<>();
      JsonNode ids = fixture("constants-ability-ids.json");
      if (ids != null) {
        ids.fields()
            .forEachRemaining(
                e -> {
                  for (String id : e.getKey().split(",")) {
                    names.put(id.trim(), e.getValue().asText());
                  }
                });
      }
      return id ->
          Optional.ofNullable(names.get(String.valueOf(id))).or(() -> Optional.of("ability_" + id));
    }

    @Bean
    HeroKits heroKits() throws IOException {
      ObjectMapper lenient =
          new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      JsonNode heroes = fixture("constants-heroes.json");
      JsonNode abilities = fixture("constants-abilities.json");
      return new HeroKits() {
        @Override
        public Optional<HeroConstant> hero(long heroId) {
          return Optional.ofNullable(heroes == null ? null : heroes.get(String.valueOf(heroId)))
              .map(n -> lenient.convertValue(n, HeroConstant.class));
        }

        @Override
        public Optional<AbilityConstant> ability(String name) {
          return Optional.ofNullable(abilities == null ? null : abilities.get(name))
              .map(n -> lenient.convertValue(n, AbilityConstant.class));
        }
      };
    }

    private static JsonNode fixture(String file) throws IOException {
      Path path = Path.of(System.getenv("REPLAY_FIXTURE_DIR"), file);
      return Files.exists(path) ? new ObjectMapper().readTree(path.toFile()) : null;
    }
  }

  @Autowired private AbilityDraftPickRepo draftPickRepo;
  @Autowired private AbilityDraftPoolRepo draftPoolRepo;
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
  @Autowired private PlayerRoleRepo roleRepo;
  @Autowired private PlayerRoleProfileRepo roleProfileRepo;
  @Autowired private TeamCompositionRepo compositionRepo;
  @Autowired private MatchRepo matchRepo;
  @Autowired private SwingSection swingSection;
  @Autowired private DeathSection deathSection;
  @Autowired private FightSection fightSection;
  @Autowired private MovementSection movementSection;
  @Autowired private DraftSection draftSection;
  @Autowired private CompositionSection compositionSection;
  @Autowired private HeroKits heroKits;

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void aRealReplayGoesThroughEveryStageAndLandsInPostgres() throws Exception {
    assumeLocalDatabase();
    for (String id : MATCHES) {
      MatchDomain match = match(id);

      ReplayStatus status = runService.process(match);

      ReplayParseRunDomain run =
          runRepo.findByMatchIdAndParserVersion(match.getId(), 1).orElseThrow();
      assertThat(status).as("%s: %s", id, run.getError()).isEqualTo(ReplayStatus.DONE);
      assertThat(playerRepo.findByMatchId(match.getId())).hasSize(10);
      assertThat(draftPickRepo.findByMatchId(match.getId())).as("%s has no draft", id).isEmpty();
      assertThat(draftPoolRepo.findAll().stream().filter(d -> d.getMatchId().equals(match.getId())))
          .isEmpty();
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
      assertThat(roleRepo.findByMatchId(match.getId()))
          .as("%s: each team has each role once", id)
          .hasSize(10)
          .extracting(r -> r.getRole() + (r.getPlayerSlot() >= 128 ? 10 : 0))
          .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 11, 12, 13, 14, 15);
      assertThat(roleProfileRepo.findByMatchId(match.getId()))
          .as("%s: one final profile per player", id)
          .hasSize(10)
          .allSatisfy(p -> assertThat(p.getStage()).isEqualTo("FINAL"));
      assertThat(compositionRepo.findByMatchId(match.getId()))
          .as("%s: one final composition per team", id)
          .hasSize(2)
          .allSatisfy(
              c -> {
                assertThat(c.getStage()).isEqualTo("FINAL");
                assertThat(c.getSummary()).isNotBlank();
              })
          .extracting(c -> c.getTeam())
          .containsExactlyInAnyOrder("RADIANT", "DIRE");
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
      report.addAll(compositionSection.lines(input));
      System.out.println("REPORT " + id + "\n" + String.join("\n", report));
      assertThat(report).isNotEmpty();
    }
    ReplayStatus again = runService.process(match(MATCHES[0]));
    assertThat(again).as("a second run replaces the first").isEqualTo(ReplayStatus.DONE);
    assertThat(deathRepo.findByMatchId(Long.parseLong(MATCHES[0]))).hasSizeGreaterThan(40);
    dataService.deleteByMatchId(Long.parseLong(MATCHES[0]));
    assertThat(deathRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
  }

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void anAbilityDraftReplayGetsIntentAndFinalRolesAndAPickAndAFinalVerdict() throws Exception {
    assumeLocalDatabase();
    for (String id : ABILITY_DRAFT_MATCHES) {
      Path fixture = Path.of(System.getenv("REPLAY_FIXTURE_DIR"), id + ".dem.zst");
      Assumptions.assumeTrue(Files.exists(fixture), id + " is not in the fixture directory");
      MatchDomain match = match(id);

      ReplayStatus status = runService.process(match);

      ReplayParseRunDomain run =
          runRepo.findByMatchIdAndParserVersion(match.getId(), 1).orElseThrow();
      assertThat(status).as("%s: %s", id, run.getError()).isEqualTo(ReplayStatus.DONE);
      assertThat(draftPickRepo.findByMatchId(match.getId())).as("%s draft", id).hasSize(40);
      assertThat(roleRepo.findByMatchId(match.getId()))
          .as("%s: each team has each role once", id)
          .hasSize(10)
          .extracting(r -> r.getRole() + (r.getPlayerSlot() >= 128 ? 10 : 0))
          .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 11, 12, 13, 14, 15);
      List<PlayerRoleProfileDomain> profiles = roleProfileRepo.findByMatchId(match.getId());
      assertThat(profiles).as("%s: intent and final profiles", id).hasSize(20);
      assertThat(compositionRepo.findByMatchId(match.getId()))
          .as("%s: a pick and a final verdict per team", id)
          .extracting(c -> c.getTeam() + " " + c.getStage())
          .containsExactlyInAnyOrder("RADIANT PICK", "RADIANT FINAL", "DIRE PICK", "DIRE FINAL");
      printAbilityDraft(id, match.getId(), profiles);
    }
  }

  private void printAbilityDraft(String id, long matchId, List<PlayerRoleProfileDomain> profiles) {
    Map<Long, List<String>> held = new java.util.TreeMap<>();
    draftPickRepo.findByMatchId(matchId).stream()
        .sorted(java.util.Comparator.comparing(p -> p.getPickOrder()))
        .forEach(
            p ->
                held.computeIfAbsent(p.getPlayerSlot(), s -> new ArrayList<>())
                    .add(p.getAbilityName()));
    StringBuilder out = new StringBuilder("AD ROLES " + id + "\n");
    for (PlayerMatchStatisticDomain stat :
        playerStatRepo.findAllByMatchId(matchId).stream()
            .sorted(java.util.Comparator.comparing(PlayerMatchStatisticDomain::getPlayerSlot))
            .toList()) {
      long slot = stat.getPlayerSlot();
      PlayerRoleProfileDomain intent = stageRow(profiles, slot, "INTENT");
      PlayerRoleProfileDomain fin = stageRow(profiles, slot, "FINAL");
      out.append(
          String.format(
              "  slot %3d %-18s nw %6d lane_role %d intent %s (%.2f) final %s (%.2f) | %s%n"
                  + "      skills %s%n      intent %s%n      final  %s%n",
              slot,
              heroKits
                  .hero(stat.getHeroId())
                  .map(h -> h.getLocalized_name())
                  .orElse("hero " + stat.getHeroId()),
              stat.getNetWorth(),
              stat.getLaneRole(),
              intent == null ? "-" : intent.getRole(),
              intent == null ? 0.0 : intent.getConfidence(),
              fin == null ? "-" : fin.getRole(),
              fin == null ? 0.0 : fin.getConfidence(),
              fin == null ? "" : fin.getEvidence(),
              held.getOrDefault(slot, List.of()),
              intent == null ? "" : functions(intent),
              fin == null ? "" : functions(fin)));
    }
    compositionRepo.findByMatchId(matchId).stream()
        .sorted(java.util.Comparator.comparing(c -> c.getTeam() + c.getStage()))
        .forEach(
            c ->
                out.append(
                    String.format(
                        "  %s %s: [%s] %s%n",
                        c.getTeam(), c.getStage(), c.getTokens(), c.getSummary())));
    System.out.println(out);
  }

  private static PlayerRoleProfileDomain stageRow(
      List<PlayerRoleProfileDomain> profiles, long slot, String stage) {
    return profiles.stream()
        .filter(p -> p.getPlayerSlot() == slot && p.getStage().equals(stage))
        .findFirst()
        .orElse(null);
  }

  private static String functions(PlayerRoleProfileDomain p) {
    return String.format(
        "scores %s farm %s jungle %s roam %s init %s disable %s save %s dmg %s dur %s",
        p.getRoleScores(),
        fmt(p.getFarmPriority()),
        fmt(p.getJungle()),
        fmt(p.getRoaming()),
        fmt(p.getInitiation()),
        fmt(p.getDisable()),
        fmt(p.getSaveHeal()),
        fmt(p.getDamage()),
        fmt(p.getDurability()));
  }

  private static String fmt(Double value) {
    return value == null ? "-" : String.format("%.2f", value);
  }

  private static void assumeLocalDatabase() {
    String url = System.getenv("SMOKE_PG_URL");
    Assumptions.assumeTrue(
        url != null
            && System.getenv("REPLAY_FIXTURE_DIR") != null
            && (url.contains("localhost") || url.contains("127.0.0.1")),
        "The smoke test needs SMOKE_PG_URL and REPLAY_FIXTURE_DIR, and changes the schema and"
            + " commits data, so it only runs against localhost");
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
    matchRepo.save(match);
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
      stat.setHeroId(p.get("hero_id").asLong());
      stat.setLaneRole(p.get("lane_role").asLong());
      stat.setNetWorth(p.get("net_worth").asLong());
      playerStatRepo.save(stat);
    }
    return match;
  }
}
