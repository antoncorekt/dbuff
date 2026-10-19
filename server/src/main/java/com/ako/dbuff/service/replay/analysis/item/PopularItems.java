package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.service.replay.analysis.item.OpenDotaItemPopularity.Phase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The fallback when the stored matches give no alternative with enough games: the items OpenDota
 * lists as popular for the hero in the purchase's phase, within the cost band. Names only.
 */
@Slf4j
@Component
public class PopularItems {
  static final Duration TTL = Duration.ofHours(24);
  static final Duration FAILURE_TTL = Duration.ofMinutes(10);
  private static final int SHOWN = 2;

  private record Cached(Instant expires, Map<Phase, List<Long>> phases) {}

  private final OpenDotaItemPopularity source;
  private final ItemKits kits;
  private final Clock clock;
  private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

  @Autowired
  public PopularItems(OpenDotaItemPopularity source, ItemKits kits) {
    this(source, kits, Clock.systemUTC());
  }

  PopularItems(OpenDotaItemPopularity source, ItemKits kits, Clock clock) {
    this.source = source;
    this.kits = kits;
    this.clock = clock;
  }

  /** Up to 2 keys, most popular first; empty when OpenDota is unavailable. */
  public List<String> near(
      long heroId, double seconds, long cost, double costBand, Set<String> exclude) {
    return phases(heroId).getOrDefault(Phase.at(seconds), List.of()).stream()
        .map(kits::key)
        .flatMap(Optional::stream)
        .filter(key -> !exclude.contains(key) && within(key, cost, costBand))
        .distinct()
        .limit(SHOWN)
        .toList();
  }

  private boolean within(String key, long cost, double band) {
    OptionalLong other = kits.cost(key);
    return other.isPresent() && Math.abs(other.getAsLong() - cost) <= band * cost;
  }

  private Map<Phase, List<Long>> phases(long heroId) {
    Instant now = clock.instant();
    Cached cached = cache.get(heroId);
    if (cached != null && now.isBefore(cached.expires())) {
      return cached.phases();
    }
    try {
      Map<Phase, List<Long>> fetched = source.fetch(heroId);
      cache.put(heroId, new Cached(now.plus(TTL), fetched));
      return fetched;
    } catch (ApiException | RuntimeException e) {
      log.warn("OpenDota item popularity for hero {} failed: {}", heroId, e.toString());
      cache.put(heroId, new Cached(now.plus(FAILURE_TTL), Map.of()));
      return Map.of();
    }
  }
}
