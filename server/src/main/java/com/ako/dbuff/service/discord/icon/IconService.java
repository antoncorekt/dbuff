package com.ako.dbuff.service.discord.icon;

import com.ako.dbuff.dao.model.DiscordIconDomain;
import com.ako.dbuff.dao.repo.DiscordIconRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.HeroConstant;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The single way a report puts an icon before a name. The chain is fixed: the synced emoji, else a
 * Unicode placeholder of the kind, else nothing. The result is a prefix ready to put before the
 * name (with its trailing space) or an empty string; with icons switched off it is always empty, so
 * output stays as it was.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IconService {
  static final String FALLBACK_METER = "dbuff.discord.icon.fallback";

  private final IconProperties properties;
  private final DiscordIconRepo repo;
  private final ConstantsManagers constants;
  private final MeterRegistry registry;

  private volatile Snapshot snapshot;
  private volatile long lastIncompleteLoadNanos;

  /** How long an incomplete snapshot is used before the next try to build a full one. */
  long retryIntervalNanos = TimeUnit.SECONDS.toNanos(30);

  private record Snapshot(
      Map<IconKind, Map<String, String>> mentions,
      Map<Long, String> heroKeysById,
      boolean complete) {}

  public String hero(long heroId) {
    if (!properties.isEnabled()) {
      return "";
    }
    return prefix(IconKind.HERO, current().heroKeysById().get(heroId));
  }

  /** An ability by its internal name; an {@code item_} name is looked up as the item. */
  public String ability(String internalName) {
    if (!properties.isEnabled() || internalName == null || internalName.isBlank()) {
      return "";
    }
    if (internalName.startsWith(IconCandidates.ITEM_PREFIX)) {
      return item(internalName.substring(IconCandidates.ITEM_PREFIX.length()));
    }
    return prefix(IconKind.ABILITY, internalName);
  }

  /** An item by its key, without the {@code item_} prefix. */
  public String item(String key) {
    return properties.isEnabled() ? prefix(IconKind.ITEM, key) : "";
  }

  /** Reads the table again; called after a sync or a check changed it. */
  public void refresh() {
    Snapshot fresh = load();
    snapshot = fresh;
    lastIncompleteLoadNanos = fresh.complete() ? 0 : System.nanoTime();
  }

  private String prefix(IconKind kind, String key) {
    String mention = key == null ? null : current().mentions().get(kind).get(key);
    if (mention != null) {
      return mention + " ";
    }
    registry.counter(FALLBACK_METER, "kind", kind.name()).increment();
    return properties.isPlaceholders() ? kind.getPlaceholder() + " " : "";
  }

  private Snapshot current() {
    Snapshot known = snapshot;
    if (known == null || (!known.complete() && retryDue())) {
      known = load();
      snapshot = known;
      lastIncompleteLoadNanos = known.complete() ? 0 : System.nanoTime();
    }
    return known;
  }

  private boolean retryDue() {
    return System.nanoTime() - lastIncompleteLoadNanos >= retryIntervalNanos;
  }

  private Snapshot load() {
    Map<IconKind, Map<String, String>> mentions = new EnumMap<>(IconKind.class);
    for (IconKind kind : IconKind.values()) {
      mentions.put(kind, new HashMap<>());
    }
    for (DiscordIconDomain row : repo.findByStatus(IconStatus.SYNCED)) {
      if (row.getEmojiId() != null && row.getEmojiName() != null) {
        mentions
            .get(row.getKind())
            .put(row.getIconKey(), "<:" + row.getEmojiName() + ":" + row.getEmojiId() + ">");
      }
    }
    Map<Long, String> heroKeys = new HashMap<>();
    boolean complete = loadHeroKeys(heroKeys);
    return new Snapshot(mentions, heroKeys, complete);
  }

  /** Fills the keys; false when the constants could not be read, so the snapshot is not final. */
  private boolean loadHeroKeys(Map<Long, String> keys) {
    Map<String, HeroConstant> heroes;
    try {
      heroes = constants.getHeroConstantMap();
    } catch (RuntimeException e) {
      log.warn("Hero constants unavailable for icons: {}", e.toString());
      return false;
    }
    for (HeroConstant hero : heroes.values()) {
      try {
        String key = IconCandidates.heroKey(hero);
        if (key != null && hero.getId() != null) {
          keys.put(Long.parseLong(hero.getId()), key);
        }
      } catch (RuntimeException e) {
        log.warn("Hero {} skipped for icons: {}", hero.getId(), e.toString());
      }
    }
    return true;
  }
}
