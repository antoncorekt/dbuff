package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.config.DotaApiConfig;
import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.DeathEventDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.TeamTimelineDomain;
import com.ako.dbuff.dao.model.TeamfightDomain;
import com.ako.dbuff.dao.model.TeamfightPlayerDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.DeathEventRepo;
import com.ako.dbuff.dao.repo.DraftEventRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.RuneEventRepo;
import com.ako.dbuff.dao.repo.TeamTimelineRepo;
import com.ako.dbuff.dao.repo.TeamfightPlayerRepo;
import com.ako.dbuff.dao.repo.TeamfightRepo;
import com.ako.dbuff.dao.repo.WardEventRepo;
import com.ako.dbuff.dotapi.invoker.JSON;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.discord.icon.IconService;
import com.ako.dbuff.service.discord.icon.TestIcons;
import com.ako.dbuff.service.match.data.DraftEventMapper;
import com.ako.dbuff.service.match.data.RuneEventMapper;
import com.ako.dbuff.service.match.data.WardEventMapper;
import com.ako.dbuff.service.replay.AbilityNames;
import com.ako.dbuff.service.replay.analysis.item.ItemKits;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.report.AbilityLabels;
import com.ako.dbuff.service.replay.report.ConstantAbilityLabels;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
  static synchronized void replayServer(DynamicPropertyRegistry registry) throws IOException {
    if (server == null) {
      server = startServer();
    }
    registry.add(
        "replay.processing.url-template",
        () -> "http://localhost:" + server.getAddress().getPort() + "/replay%d/570/%d_%d.dem.bz2");
    registry.add(
        "replay.processing.temp-dir", () -> System.getProperty("java.io.tmpdir") + "/smoke");
  }

  /**
   * One server for the whole JVM: Spring caches the context of a test class together with this
   * server's port, so a server stopped after one class leaves the next class downloading from a
   * dead port.
   */
  private static HttpServer startServer() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String file = Path.of(exchange.getRequestURI().getPath()).getFileName().toString();
          String match = file.substring(0, file.indexOf('_'));
          Path replay = fixture(match + ".dem.zst");
          exchange.sendResponseHeaders(200, Files.size(replay));
          Files.copy(replay, exchange.getResponseBody());
          exchange.close();
        });
    server.start();
    return server;
  }

  @AfterAll
  static void stop() throws IOException {
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
    SimpleMeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    IconService iconService() {
      return TestIcons.off();
    }

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

    /**
     * The item constants of OpenDota's {@code /constants} answer, from {@code
     * constants-items.json}.
     */
    @Bean
    ItemKits itemKits() throws IOException {
      ObjectMapper lenient =
          new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      JsonNode items = constants("constants-items.json");
      Map<Long, String> keys = new HashMap<>();
      if (items != null) {
        items
            .fields()
            .forEachRemaining(
                e -> {
                  if (e.getValue().hasNonNull("id")) {
                    keys.put(e.getValue().get("id").asLong(), e.getKey());
                  }
                });
      }
      return new ItemKits() {
        @Override
        public Optional<ItemConstant> item(String key) {
          return Optional.ofNullable(items == null ? null : items.get(key))
              .map(n -> lenient.convertValue(n, ItemConstant.class));
        }

        @Override
        public Optional<String> key(long id) {
          return Optional.ofNullable(keys.get(id));
        }
      };
    }

    @Bean
    DotaApiConfig.DotaApiConfigurationProperties dotaApiConfigurationProperties() {
      return new DotaApiConfig.DotaApiConfigurationProperties();
    }

    @Bean
    RateLimiter dotaApiRateLimiter() {
      return RateLimiter.create(1_000);
    }

    /** The real label logic over the fixture's hero and ability constants. */
    @Bean
    AbilityLabels abilityLabels() throws IOException {
      ObjectMapper lenient =
          new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      Map<String, AbilityConstant> abilities = new HashMap<>();
      Map<String, HeroConstant> heroes = new HashMap<>();
      JsonNode abilityJson = constants("constants-abilities.json");
      if (abilityJson != null) {
        abilityJson
            .fields()
            .forEachRemaining(
                e ->
                    abilities.put(
                        e.getKey(), lenient.convertValue(e.getValue(), AbilityConstant.class)));
      }
      JsonNode heroJson = constants("constants-heroes.json");
      if (heroJson != null) {
        heroJson
            .fields()
            .forEachRemaining(
                e ->
                    heroes.put(e.getKey(), lenient.convertValue(e.getValue(), HeroConstant.class)));
      }
      ConstantsManagers constants = mock(ConstantsManagers.class);
      when(constants.getAllAbilityConstants()).thenReturn(abilities);
      when(constants.getHeroConstantMap()).thenReturn(heroes);
      return new ConstantAbilityLabels(constants, TestIcons.off());
    }

    private static JsonNode constants(String file) throws IOException {
      Path path = fixture(file);
      return Files.exists(path) ? new ObjectMapper().readTree(path.toFile()) : null;
    }
  }

  @Autowired private PlayerGameStatisticRepo playerStatRepo;
  @Autowired private PlayerTimelineRepo timelineRepo;
  @Autowired protected MatchRepo matchRepo;
  @Autowired private AbilityRepo abilityRepo;
  @Autowired private DeathEventRepo deathEventRepo;
  @Autowired private TeamfightRepo teamfightRepo;
  @Autowired private TeamfightPlayerRepo teamfightPlayerRepo;
  @Autowired private TeamTimelineRepo teamTimelineRepo;
  @Autowired private RuneEventRepo runeEventRepo;
  @Autowired private WardEventRepo wardEventRepo;
  @Autowired private DraftEventRepo draftEventRepo;

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
      stat.setHeroDamage(optLong(p, "hero_damage"));
      stat.setHeroHealing(optLong(p, "hero_healing"));
      stat.setDamageTaken(heroDamageTaken(p));
      stat.setLastHits(optLong(p, "last_hits"));
      stat.setObsPlaced(optLong(p, "obs_placed"));
      stat.setSenPlaced(optLong(p, "sen_placed"));
      playerStatRepo.save(stat);
    }
    seedOpenDotaFights(match.getId(), json);
    seedTeamTimeline(match.getId(), json);
    seedMatchLogs(match, id);
    seedAbilities(match.getId(), json);
    return match;
  }

  /** The skills a hero leveled and the damage each did, as the match detail import stores them. */
  private void seedAbilities(long matchId, JsonNode json) throws IOException {
    abilityRepo.deleteByMatchId(matchId);
    Path idsFile = fixture("constants-ability-ids.json");
    if (!Files.exists(idsFile)) {
      return;
    }
    Map<Long, String> names = new HashMap<>();
    new ObjectMapper()
        .readTree(idsFile.toFile())
        .fields()
        .forEachRemaining(
            e -> names.put(Long.parseLong(e.getKey().split(",")[0]), e.getValue().asText()));
    for (JsonNode p : json.path("players")) {
      Set<Long> leveled = new HashSet<>();
      p.path("ability_upgrades_arr").forEach(id -> leveled.add(id.asLong()));
      for (Long abilityId : leveled) {
        String name = names.get(abilityId);
        if (name == null) {
          continue;
        }
        abilityRepo.save(
            AbilityDomain.builder()
                .matchId(matchId)
                .playerSlot(p.path("player_slot").asLong())
                .abilityId(abilityId)
                .playerId(optLong(p, "account_id"))
                .name(name)
                .damageDealt(optLong(p.path("damage_inflictor"), name))
                .useCount(optLong(p.path("ability_uses"), name))
                .build());
      }
    }
  }

  /** The ward and rune logs of OpenDota's match data, as the match detail import stores them. */
  private void seedMatchLogs(MatchDomain match, String id) throws IOException {
    wardEventRepo.deleteByMatchId(match.getId());
    runeEventRepo.deleteByMatchId(match.getId());
    draftEventRepo.deleteByMatchId(match.getId());
    MatchResponse response =
        new JSON().getMapper().readValue(fixture(id + ".json").toFile(), MatchResponse.class);
    new WardEventMapper(wardEventRepo).map(response, match);
    new RuneEventMapper(runeEventRepo).map(response, match);
    new DraftEventMapper(draftEventRepo).map(response, match);
  }

  /** OpenDota's per-minute Radiant gold and experience advantage. */
  private void seedTeamTimeline(long matchId, JsonNode json) {
    teamTimelineRepo.deleteByMatchId(matchId);
    JsonNode gold = json.path("radiant_gold_adv");
    JsonNode xp = json.path("radiant_xp_adv");
    for (int minute = 0; minute < Math.max(gold.size(), xp.size()); minute++) {
      teamTimelineRepo.save(
          TeamTimelineDomain.builder()
              .matchId(matchId)
              .gameMinute(minute)
              .radiantGoldAdv(gold.has(minute) ? gold.get(minute).asLong() : null)
              .radiantXpAdv(xp.has(minute) ? xp.get(minute).asLong() : null)
              .build());
    }
  }

  private static Long heroDamageTaken(JsonNode player) {
    JsonNode taken = player.get("damage_taken");
    if (taken == null || !taken.isObject()) {
      return null;
    }
    long total = 0;
    for (var entry : taken.properties()) {
      if (entry.getKey().startsWith("npc_dota_hero")) {
        total += entry.getValue().asLong();
      }
    }
    return total;
  }

  /**
   * The deaths with their gold and the teamfights of OpenDota's JSON, as its parser stores them.
   */
  private void seedOpenDotaFights(long matchId, JsonNode json) {
    deathEventRepo.deleteByMatchId(matchId);
    teamfightPlayerRepo.deleteByMatchId(matchId);
    teamfightRepo.deleteByMatchId(matchId);
    JsonNode players = json.path("players");
    for (JsonNode p : players) {
      long slot = p.path("player_slot").asLong();
      int index = 0;
      for (JsonNode death : p.path("deaths_log")) {
        deathEventRepo.save(
            DeathEventDomain.builder()
                .matchId(matchId)
                .playerSlot(slot)
                .deathIndex(index++)
                .deathTime(optLong(death, "time"))
                .killerHeroName(death.path("key").asText(null))
                .goldLost(optLong(death, "gold_lost"))
                .goldFed(optLong(death, "gold_fed"))
                .timeDead(optLong(death, "time_dead"))
                .build());
      }
    }
    int fightIndex = 0;
    for (JsonNode fight : json.path("teamfights")) {
      teamfightRepo.save(
          TeamfightDomain.builder()
              .matchId(matchId)
              .fightIndex(fightIndex)
              .startTime(optLong(fight, "start"))
              .endTime(optLong(fight, "end"))
              .lastDeathTime(optLong(fight, "last_death"))
              .deaths(optLong(fight, "deaths"))
              .build());
      for (int i = 0; i < players.size() && i < fight.path("players").size(); i++) {
        JsonNode entry = fight.path("players").get(i);
        teamfightPlayerRepo.save(
            TeamfightPlayerDomain.builder()
                .matchId(matchId)
                .fightIndex(fightIndex)
                .playerSlot(players.get(i).path("player_slot").asLong())
                .deaths(optLong(entry, "deaths"))
                .goldDelta(optLong(entry, "gold_delta"))
                .build());
      }
      fightIndex++;
    }
  }

  private static Long optLong(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asLong() : null;
  }
}
