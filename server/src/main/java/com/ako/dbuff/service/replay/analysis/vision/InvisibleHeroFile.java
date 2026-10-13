package com.ako.dbuff.service.replay.analysis.vision;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The checked-in list ({@code replay/invisible-heroes.json}) of the heroes whose kit makes them
 * invisible and the items that do. It is read and checked on first use, strictly: a typo makes the
 * vision analysis fail with the entry named, and does not stop the application. An ability counts
 * for any hero that owns it, so a hero drafted with the ability in Ability Draft is covered; the
 * hero key only names who the ability belongs to and must be the start of the ability's name.
 *
 * <pre>
 * {"heroes": {"npc_dota_hero_clinkz": {"ability": "clinkz_wind_walk", "mode": "ACTIVE"}},
 *  "items": ["item_glimmer_cape"]}
 * </pre>
 */
@Component
public class InvisibleHeroFile implements InvisibleHeroes {
  private static final String HERO_PREFIX = "npc_dota_hero_";
  private static final Pattern HERO_KEY =
      Pattern.compile("npc_dota_hero_[a-z][a-z0-9]*(_[a-z0-9]+)*");
  private static final Pattern ABILITY = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)+");
  private static final Pattern ITEM = Pattern.compile("item_[a-z][a-z0-9]*(_[a-z0-9]+)*");

  private record Entry(String ability, String mode) {}

  private record Content(Map<String, Entry> heroes, List<String> items) {}

  private record Loaded(Map<String, Source> abilities, Set<String> items) {}

  private final Supplier<InputStream> input;
  private final String source;
  private volatile Loaded loaded;

  @Autowired
  public InvisibleHeroFile(
      @Value("${replay.analysis.vision.invisible-heroes:replay/invisible-heroes.json}")
          String resource) {
    this(() -> open(resource), resource);
  }

  InvisibleHeroFile(Supplier<InputStream> input, String source) {
    this.input = input;
    this.source = source;
  }

  /** The file is read and checked on first use, so a bad file never stops the application. */
  @Override
  public Map<String, Source> abilities() {
    return loaded().abilities();
  }

  @Override
  public Set<String> items() {
    return loaded().items();
  }

  private Loaded loaded() {
    Loaded now = loaded;
    if (now == null) {
      synchronized (this) {
        now = loaded;
        if (now == null) {
          now = load();
          loaded = now;
        }
      }
    }
    return now;
  }

  private Loaded load() {
    Content content = read(input.get(), source);
    if (content.heroes() == null || content.heroes().isEmpty()) {
      throw invalid(source, "has no heroes");
    }
    Map<String, Source> abilities = new LinkedHashMap<>();
    content
        .heroes()
        .forEach(
            (key, entry) -> {
              if (!HERO_KEY.matcher(key).matches()) {
                throw invalid(source, "hero key '" + key + "' is not like npc_dota_hero_riki");
              }
              if (entry == null
                  || entry.ability() == null
                  || !ABILITY.matcher(entry.ability()).matches()) {
                throw invalid(source, "'" + key + "' has no ability like riki_smoke_screen");
              }
              if (!entry.ability().startsWith(key.substring(HERO_PREFIX.length()) + "_")) {
                throw invalid(
                    source,
                    "'" + key + "' has the ability '" + entry.ability() + "' of another hero");
              }
              if (!"ACTIVE".equals(entry.mode()) && !"PASSIVE".equals(entry.mode())) {
                throw invalid(
                    source,
                    "'" + key + "' has the mode '" + entry.mode() + "', not ACTIVE or PASSIVE");
              }
              if (abilities.put(
                      entry.ability(),
                      new Source(key, entry.ability(), "PASSIVE".equals(entry.mode())))
                  != null) {
                throw invalid(source, "the ability '" + entry.ability() + "' is listed twice");
              }
            });
    if (content.items() == null) {
      throw invalid(source, "has no items");
    }
    Set<String> items = new HashSet<>();
    for (String item : content.items()) {
      if (item == null || !ITEM.matcher(item).matches()) {
        throw invalid(source, "item '" + item + "' is not like item_glimmer_cape");
      }
      if (!items.add(item)) {
        throw invalid(source, "the item '" + item + "' is listed twice");
      }
    }
    return new Loaded(Map.copyOf(abilities), Set.copyOf(items));
  }

  private static Content read(InputStream in, String source) {
    try (in) {
      ObjectMapper mapper =
          new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
      Map<String, Object> root = mapper.readValue(in, new TypeReference<>() {});
      if (root == null) {
        throw invalid(source, "is empty");
      }
      for (String section : root.keySet()) {
        if (!section.equals("heroes") && !section.equals("items")) {
          throw invalid(source, "unknown section '" + section + "'");
        }
      }
      return new Content(
          mapper.convertValue(root.get("heroes"), new TypeReference<>() {}),
          mapper.convertValue(root.get("items"), new TypeReference<>() {}));
    } catch (IOException | IllegalArgumentException e) {
      throw new IllegalStateException(
          "Invisible hero file " + source + ": cannot be read: " + e.getMessage(), e);
    }
  }

  private static InputStream open(String resource) {
    try {
      return new ClassPathResource(resource).getInputStream();
    } catch (IOException e) {
      throw new IllegalStateException("Invisible hero file " + resource + ": cannot be opened", e);
    }
  }

  private static IllegalStateException invalid(String source, String problem) {
    return new IllegalStateException("Invisible hero file " + source + ": " + problem);
  }
}
