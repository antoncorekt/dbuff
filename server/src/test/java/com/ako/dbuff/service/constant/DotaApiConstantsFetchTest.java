package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.DotaApiConfig;
import com.ako.dbuff.config.DotaApiConfig.DotaApiConfigurationProperties;
import com.ako.dbuff.config.DotaApiConstant;
import com.ako.dbuff.dao.repo.ConstantDomainRepo;
import com.ako.dbuff.dotapi.api.ConstantsApi;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.PatchConstant;
import com.google.common.util.concurrent.RateLimiter;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.ProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Runs the constants through the {@link ApiClient} built by {@link DotaApiConfig} against a local
 * server, because the generated oneOf response only deserializes with the right mapper settings.
 */
class DotaApiConstantsFetchTest {

  private static final String PATCH =
      "[{\"name\":\"7.37\",\"date\":\"2024-05-22T00:00:00Z\",\"id\":57},"
          + "{\"name\":\"7.38\",\"date\":\"2025-02-19T00:00:00Z\",\"id\":58}]";

  private HttpServer server;
  private ConstantFetcherService fetcher;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    serve("/constants/heroes", resource("constants_heroes.json"));
    serve("/constants/abilities", resource("constants_abilities.json"));
    serve("/constants/patch", PATCH);
    serve("/constants/ability_ids", "{\"5003\":\"antimage_mana_break\"}");
    serve(
        "/constants/hero_abilities",
        "{\"npc_dota_hero_axe\":{\"abilities\":[\"axe_berserkers_call\"]}}");
    serve("/constants/items", "{\"blink\":{\"id\":1,\"dname\":\"Blink Dagger\",\"cost\":2250}}");
    serve("/constants/game_mode", "{\"1\":{\"id\":1,\"name\":\"game_mode_all_pick\"}}");
    serve("/constants/bad", "[{\"id\":1},\"x\"]");
    server.start();

    fetcher = new ConstantFetcherService(new ConstantsApi(client()), RateLimiter.create(1000));
  }

  private ApiClient client() {
    ApiClient apiClient = new DotaApiConfig(new DotaApiConfigurationProperties()).getApiClient();
    apiClient.setBasePath("http://127.0.0.1:" + server.getAddress().getPort());
    return apiClient;
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void objectConstants_areFetchedAndConverted() {
    Map<String, HeroConstant> heroes =
        new HeroConstantService(mock(ConstantDomainRepo.class), fetcher).getConstantMap();

    assertEquals("Anti-Mage", heroes.get("1").getLocalized_name());
    assertEquals(3, heroes.size());
  }

  @Test
  void arrayConstant_isFetchedAndConverted() {
    Map<String, PatchConstant> patches =
        new PatchConstantService(mock(ConstantDomainRepo.class), fetcher).getConstantMap();

    assertEquals("7.38", patches.get("58").getName());
  }

  @Test
  void everyConstantType_isFetchedAsTheRightVariantWithItsContent() {
    assertEquals(
        "game_mode_all_pick",
        mapOf(DotaApiConstant.GAME_MODE).get("1") instanceof Map<?, ?> m ? m.get("name") : null);
    assertTrue(mapOf(DotaApiConstant.HERO_ABILITIES).containsKey("npc_dota_hero_axe"));
    assertEquals("antimage_mana_break", mapOf(DotaApiConstant.ABILITIES_IDS).get("5003"));
    assertEquals(6, mapOf(DotaApiConstant.ABILITIES).size());
    assertEquals(3, mapOf(DotaApiConstant.HERO).size());
    assertEquals(
        "Blink Dagger",
        new ItemConstantService(mock(ConstantDomainRepo.class), fetcher)
            .getConstantMap()
            .get("blink")
            .getDname());
    assertEquals(
        2,
        fetcher
            .fetch(DotaApiConstant.PATCH)
            .getListGetConstantsByResource200ResponseOneOfInner()
            .size());
  }

  @Test
  void arrayElementOfAnotherShape_failsWithItsIndex() {
    ConstantsApi api = new ConstantsApi(client());

    ProcessingException e =
        assertThrows(ProcessingException.class, () -> api.getConstantsByResource("bad"));

    Throwable cause = e;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    assertTrue(cause.getMessage().contains("element 1"), cause.getMessage());
  }

  private Map<String, Object> mapOf(DotaApiConstant constant) {
    return fetcher.fetch(constant).getMapStringObject();
  }

  private void serve(String path, String body) {
    server.createContext(
        path,
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
  }

  private String resource(String name) throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/opendota/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
