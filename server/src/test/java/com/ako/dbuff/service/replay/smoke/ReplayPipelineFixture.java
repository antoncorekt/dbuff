package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.AbilityNames;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What a test that runs a fixture replay through the pipeline needs: a local HTTP server standing
 * in for Valve, which serves {@code <id>.dem.zst} from {@code REPLAY_FIXTURE_DIR}, and the match
 * and player rows that OpenDota's JSON ({@code <id>.json}) would have given the application.
 */
public abstract class ReplayPipelineFixture {

  /** Net worth is never zero, as gold is at the first minute; this stands in for the items. */
  private static final long STARTING_WORTH = 4000;

  private static HttpServer server;

  @DynamicPropertySource
  static void replayServer(DynamicPropertyRegistry registry) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String file = Path.of(exchange.getRequestURI().getPath()).getFileName().toString();
          String match = file.substring(0, file.indexOf('_'));
          byte[] body = Files.readAllBytes(fixture(match + ".dem.zst"));
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

    @Bean
    HeroKits heroKits() {
      return new HeroKits() {
        @Override
        public Optional<HeroConstant> hero(long heroId) {
          return Optional.empty();
        }

        @Override
        public Optional<AbilityConstant> ability(String name) {
          return Optional.empty();
        }
      };
    }
  }

  @Autowired private PlayerGameStatisticRepo playerStatRepo;
  @Autowired private PlayerTimelineRepo timelineRepo;
  @Autowired private MatchRepo matchRepo;

  protected static Path fixture(String fileName) {
    return Path.of(System.getenv("REPLAY_FIXTURE_DIR"), fileName);
  }

  protected static boolean hasFixture(String id) {
    return Files.exists(fixture(id + ".json")) && Files.exists(fixture(id + ".dem.zst"));
  }

  /** The pipeline changes the schema and commits data, so it only runs against this machine. */
  protected static boolean isLocalDatabase() {
    String url = System.getenv("SMOKE_PG_URL");
    return url == null || url.contains("localhost") || url.contains("127.0.0.1");
  }

  protected MatchDomain seedMatch(String id) throws IOException {
    JsonNode json = new ObjectMapper().readTree(fixture(id + ".json").toFile());
    MatchDomain match = new MatchDomain();
    match.setId(Long.parseLong(id));
    match.setReplayCluster(json.get("cluster").asLong());
    match.setReplaySalt(json.get("replay_salt").asLong());
    match.setGameModeId(json.get("game_mode").asLong());
    match.setDuration(json.get("duration").asLong());
    match.setStartTime(json.get("start_time").asLong());
    match.setRadiantWin(json.get("radiant_win").asBoolean());
    match.setRadiantScore(json.get("radiant_score").asLong());
    match.setDireScore(json.get("dire_score").asLong());
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
      stat.setPlayerSlot(slot);
      stat.setPlayerId(p.get("account_id").asLong());
      stat.setHeroId(p.get("hero_id").asLong());
      stat.setLaneRole(p.get("lane_role").asLong());
      stat.setNetWorth(p.get("net_worth").asLong());
      stat.setPositionEst(p.hasNonNull("position_est") ? p.get("position_est").asLong() : null);
      playerStatRepo.save(stat);
    }
    return match;
  }
}
