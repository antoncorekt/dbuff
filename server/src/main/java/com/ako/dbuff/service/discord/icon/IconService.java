package com.ako.dbuff.service.discord.icon;

import com.ako.dbuff.dao.model.DiscordIconDomain;
import com.ako.dbuff.dao.repo.DiscordIconRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.HeroConstant;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
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

  private record Snapshot(
      Map<IconKind, Map<String, String>> mentions, Map<Long, String> heroKeysById) {}

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
    snapshot = load();
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
    if (known == null) {
      known = load();
      snapshot = known;
    }
    return known;
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
    return new Snapshot(mentions, heroKeys());
  }

  private Map<Long, String> heroKeys() {
    Map<Long, String> keys = new HashMap<>();
    try {
      for (HeroConstant hero : constants.getHeroConstantMap().values()) {
        String key = IconCandidates.heroKey(hero);
        if (key != null && hero.getId() != null) {
          keys.put(Long.parseLong(hero.getId()), key);
        }
      }
    } catch (RuntimeException e) {
      log.warn("Hero constants unavailable for icons: {}", e.toString());
    }
    return keys;
  }
}
