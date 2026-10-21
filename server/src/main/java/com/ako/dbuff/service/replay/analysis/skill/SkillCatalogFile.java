package com.ako.dbuff.service.replay.analysis.skill;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The skill catalog, from the checked-in file {@code replay/skills.yaml}. Read strictly: an unknown
 * field, a repeated key or ability, or a bad number fails with every problem named.
 */
@Component
public class SkillCatalogFile {
  private record Content(Map<String, Double> parameters, List<Entry> entries) {}

  private record Entry(
      String key, List<String> abilities, List<String> aspects, Map<String, Double> parameters) {}

  private static final ObjectMapper MAPPER =
      new ObjectMapper(new YAMLFactory())
          .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  private final Supplier<InputStream> input;
  private final String source;
  private List<SkillCatalogEntry> entries;
  private Map<String, Double> defaults;

  @Autowired
  public SkillCatalogFile(
      @Value("${replay.analysis.skill-effects.resource:replay/skills.yaml}") String resource) {
    this(() -> open(resource), resource);
  }

  SkillCatalogFile(Supplier<InputStream> input, String source) {
    this.input = input;
    this.source = source;
  }

  public synchronized List<SkillCatalogEntry> entries() {
    ensureLoaded();
    return entries;
  }

  /** The file's default parameters. */
  public synchronized Map<String, Double> defaults() {
    ensureLoaded();
    return defaults;
  }

  private void ensureLoaded() {
    if (entries == null) {
      load();
    }
  }

  private void load() {
    Content content = read();
    if (content.entries() == null || content.entries().isEmpty()) {
      throw invalid("has no entries");
    }
    List<String> problems = new ArrayList<>();
    Map<String, Double> base = numbers("parameters", content.parameters(), problems);
    Set<String> keys = new HashSet<>();
    Set<String> abilities = new HashSet<>();
    List<SkillCatalogEntry> checked = new ArrayList<>();
    for (Entry entry : content.entries()) {
      checked.add(checked(entry, base, keys, abilities, problems));
    }
    if (!problems.isEmpty()) {
      throw invalid(String.join("; ", problems));
    }
    defaults = Map.copyOf(base);
    entries = List.copyOf(checked);
  }

  private SkillCatalogEntry checked(
      Entry entry,
      Map<String, Double> base,
      Set<String> keys,
      Set<String> abilities,
      List<String> problems) {
    if (entry == null || entry.key() == null || entry.key().isBlank()) {
      throw invalid("has an entry without a key");
    }
    String key = entry.key();
    if (!keys.add(key)) {
      problems.add("'" + key + "' is listed twice");
    }
    if (blankOrEmpty(entry.aspects())) {
      problems.add("'" + key + "' lists no aspects or a blank one");
    }
    if (blankOrEmpty(entry.abilities())) {
      problems.add("'" + key + "' lists no abilities or a blank one");
    } else {
      for (String ability : entry.abilities()) {
        if (!abilities.add(ability)) {
          problems.add("ability '" + ability + "' is listed by more than one entry");
        }
      }
    }
    Map<String, Double> merged = new LinkedHashMap<>(base);
    merged.putAll(numbers("'" + key + "' parameters", entry.parameters(), problems));
    return new SkillCatalogEntry(
        key,
        entry.abilities() == null ? List.of() : List.copyOf(entry.abilities()),
        entry.aspects() == null ? List.of() : List.copyOf(entry.aspects()),
        Map.copyOf(merged));
  }

  private static boolean blankOrEmpty(List<String> names) {
    return names == null
        || names.isEmpty()
        || names.stream().anyMatch(n -> n == null || n.isBlank());
  }

  private static Map<String, Double> numbers(
      String what, Map<String, Double> map, List<String> problems) {
    Map<String, Double> out = new LinkedHashMap<>();
    if (map != null) {
      map.forEach(
          (name, value) -> {
            if (value == null || value.isNaN() || value.isInfinite() || value < 0) {
              problems.add(what + " '" + name + "' must be a number of 0 or more");
            } else {
              out.put(name, value);
            }
          });
    }
    return out;
  }

  private Content read() {
    try (InputStream in = input.get()) {
      Content content = MAPPER.readValue(in, Content.class);
      if (content == null) {
        throw invalid("is empty");
      }
      return content;
    } catch (IOException e) {
      throw new IllegalStateException(
          "Skill catalog " + source + ": cannot be read: " + e.getMessage(), e);
    }
  }

  private static InputStream open(String resource) {
    try {
      return new ClassPathResource(resource).getInputStream();
    } catch (IOException e) {
      throw new IllegalStateException("Skill catalog " + resource + ": cannot be opened", e);
    }
  }

  private IllegalStateException invalid(String problem) {
    return new IllegalStateException("Skill catalog " + source + ": " + problem);
  }
}
