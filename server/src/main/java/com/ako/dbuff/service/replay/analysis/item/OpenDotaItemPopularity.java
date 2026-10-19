package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.config.DotaApiConfig;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.ws.rs.core.GenericType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * OpenDota {@code /heroes/{hero_id}/itemPopularity}. The generated response model keeps one number
 * per phase, while the service answers an item id to count map, so the answer is read as that map.
 */
@Component
public class OpenDotaItemPopularity {
  private static final int CONNECT_TIMEOUT_MS = 3_000;
  private static final int READ_TIMEOUT_MS = 5_000;
  private static final long RATE_WAIT_SECONDS = 2;

  private final ApiClient apiClient;
  private final RateLimiter rateLimiter;

  @Autowired
  public OpenDotaItemPopularity(
      DotaApiConfig.DotaApiConfigurationProperties properties,
      @Qualifier("dotaApiRateLimiter") RateLimiter rateLimiter) {
    this(withTimeouts(properties), rateLimiter);
  }

  OpenDotaItemPopularity(ApiClient apiClient, RateLimiter rateLimiter) {
    this.apiClient = apiClient;
    this.rateLimiter = rateLimiter;
  }

  /** A client of its own, so the timeouts do not change the other OpenDota callers. */
  private static ApiClient withTimeouts(DotaApiConfig.DotaApiConfigurationProperties properties) {
    ApiClient client = new ApiClient();
    client.setConnectTimeout(CONNECT_TIMEOUT_MS);
    client.setReadTimeout(READ_TIMEOUT_MS);
    DotaApiConfig.registerApiKey(client, properties.getApiKey());
    return client;
  }

  /** The item ids of each phase, most bought first. */
  public Map<Phase, List<Long>> fetch(long heroId) throws ApiException {
    if (!rateLimiter.tryAcquire(RATE_WAIT_SECONDS, TimeUnit.SECONDS)) {
      throw new ApiException("No OpenDota request permit within " + RATE_WAIT_SECONDS + " s");
    }
    Map<String, Map<String, Number>> response =
        apiClient
            .invokeAPI(
                "HeroesApi.getHeroesByHeroIdSelectItemPopularity",
                "/heroes/" + heroId + "/itemPopularity",
                "GET",
                new ArrayList<>(),
                null,
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                apiClient.selectHeaderAccept("application/json; charset=utf-8"),
                apiClient.selectHeaderContentType(),
                null,
                new GenericType<Map<String, Map<String, Number>>>() {},
                false)
            .getData();
    Map<Phase, List<Long>> phases = new LinkedHashMap<>();
    for (Phase phase : Phase.values()) {
      Map<String, Number> counts = response == null ? null : response.get(phase.field());
      phases.put(phase, counts == null ? List.of() : mostBought(counts));
    }
    return phases;
  }

  private static List<Long> mostBought(Map<String, Number> counts) {
    return counts.entrySet().stream()
        .filter(e -> e.getKey().matches("\\d+"))
        .sorted(
            Map.Entry.<String, Number>comparingByValue(
                (a, b) -> Double.compare(b.doubleValue(), a.doubleValue())))
        .map(e -> Long.parseLong(e.getKey()))
        .toList();
  }

  /** The purchase phases OpenDota groups the popularity by. */
  public enum Phase {
    START("start_game_items", 60),
    EARLY("early_game_items", 600),
    MID("mid_game_items", 1_500),
    LATE("late_game_items", Integer.MAX_VALUE);

    private final String field;
    private final int untilSeconds;

    Phase(String field, int untilSeconds) {
      this.field = field;
      this.untilSeconds = untilSeconds;
    }

    String field() {
      return field;
    }

    public static Phase at(double seconds) {
      for (Phase phase : values()) {
        if (seconds < phase.untilSeconds) {
          return phase;
        }
      }
      return LATE;
    }
  }
}
