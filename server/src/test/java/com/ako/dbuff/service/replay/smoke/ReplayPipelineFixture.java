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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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
    AbilityNames abilityNames() throws IOException {
      Map<String, String> names = new HashMap<>();
      JsonNode ids = constants("constants-ability-ids.json");
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

    /**
     * The hero and skill constants of OpenDota's {@code /constants} answers, read from {@code
     * constants-heroes.json} and {@code constants-abilities.json} in the fixture directory; empty
     * kits when they are not there.
     */
    @Bean
    HeroKits heroKits() throws IOException {
      ObjectMapper lenient =
          new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      JsonNode heroes = constants("constants-heroes.json");
      JsonNode abilities = constants("constants-abilities.json");
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

    private static JsonNode constants(String file) throws IOException {
      Path path = fixture(file);
      return Files.exists(path) ? new ObjectMapper().readTree(path.toFile()) : null;
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

  protected MatchDomain seedMatch(String id) throws IOException {
    JsonNode json = new ObjectMapper().readTree(fixture(id + ".json").toFile());
    MatchDomain match = new MatchDomain();
    match.setId(Long.parseLong(id));
    match.setReplayCluster(optLong(json, "cluster"));
    match.setReplaySalt(optLong(json, "replay_salt"));
    match.setGameModeId(optLong(json, "game_mode"));
    match.setDuration(optLong(json, "duration"));
    match.setStartTime(optLong(json, "start_time"));
    match.setRadiantWin(
        json.hasNonNull("radiant_win") ? json.get("radiant_win").asBoolean() : null);
    match.setRadiantScore(optLong(json, "radiant_score"));
    match.setDireScore(optLong(json, "dire_score"));
    matchRepo.save(match);
    playerStatRepo.deleteAll(playerStatRepo.findAllByMatchId(match.getId()));
    timelineRepo.deleteByMatchId(match.getId());
    for (JsonNode p : json.path("players")) {
      long slot = p.path("player_slot").asLong();
      for (int minute = 0; minute < p.path("gold_t").size(); minute++) {
        long gold = p.path("gold_t").path(minute).asLong();
        timelineRepo.save(
            PlayerTimelineDomain.builder()
                .matchId(match.getId())
                .playerSlot(slot)
                .gameMinute(minute)
                .gold(gold)
                .xp(p.path("xp_t").path(minute).asLong())
                .networth(gold + STARTING_WORTH)
                .build());
      }
      PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
      stat.setMatchId(match.getId());
      stat.setPlayerSlot(slot);
      stat.setPlayerId(optLong(p, "account_id"));
      stat.setHeroId(optLong(p, "hero_id"));
      stat.setLaneRole(optLong(p, "lane_role"));
      stat.setNetWorth(optLong(p, "net_worth"));
      stat.setPositionEst(optLong(p, "position_est"));
      playerStatRepo.save(stat);
    }
    return match;
  }

  private static Long optLong(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asLong() : null;
  }
}
