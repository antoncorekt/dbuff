package com.ako.dbuff.service.replay.analysis.item;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The item catalog, from the checked-in file {@code replay/items.yaml}. Read and checked on first
 * use, so a bad file stops that use with the entries named and never the application start. Adding
 * an item is a new entry of existing aspects.
 */
@Component
public class ItemEfficiencyFile {
  private record Content(
      Map<String, Double> prices,
      Map<String, Double> alternatives,
      Map<String, String> categories,
      List<Entry> entries) {}

  private record Entry(
      String key,
      String category,
      List<String> items,
      List<String> aspects,
      List<List<String>> winStats,
      List<String> alternatives,
      Map<String, Double> parameters) {}

  private static final String ITEM_PREFIX = "item_";
  private static final ObjectMapper MAPPER =
      new ObjectMapper(new YAMLFactory())
          .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  private final Supplier<InputStream> input;
  private final String source;
  private List<ItemEfficiencyEntry> entries;
  private Map<String, String> categories;
  private Map<String, Double> alternativeSettings;
  private Map<String, Double> prices;

  @Autowired
  public ItemEfficiencyFile(
      @Value("${replay.analysis.item-efficiency.resource:replay/items.yaml}") String resource) {
    this(() -> open(resource), resource);
  }

  ItemEfficiencyFile(Supplier<InputStream> input, String source) {
    this.input = input;
    this.source = source;
  }

  public synchronized List<ItemEfficiencyEntry> entries() {
    ensureLoaded();
    return entries;
  }

  /** The numbers of the {@code alternatives} section; one the file omits is not in the map. */
  public synchronized Map<String, Double> alternativeSettings() {
    ensureLoaded();
    return alternativeSettings;
  }

  /** The category ids with their display names. */
  public synchronized Map<String, String> categories() {
    ensureLoaded();
    return categories;
  }

  /** Every category id and entry key a channel may choose, categories first. */
  public synchronized Set<String> selectableNames() {
    ensureLoaded();
    Set<String> names = new LinkedHashSet<>(categories.keySet());
    entries.forEach(entry -> names.add(entry.key()));
    return names;
  }

  /**
   * The categories and entry keys named in a comma or space separated list, in the canonical
   * spelling; empty for a blank text, {@code all} or {@code default}.
   *
   * @throws IllegalArgumentException with the valid names when one is unknown
   */
  public Optional<List<String>> parseSelection(String raw) {
    String trimmed = raw == null ? "" : raw.trim();
    if (trimmed.isEmpty()
        || "all".equalsIgnoreCase(trimmed)
        || "default".equalsIgnoreCase(trimmed)) {
      return Optional.empty();
    }
    Set<String> valid = selectableNames();
    Set<String> names = new LinkedHashSet<>();
    for (String token : trimmed.split("[,\\s]+")) {
      if (token.isEmpty()) {
        continue;
      }
      String name = token.toLowerCase(Locale.ROOT).replace('-', '_');
      if (!valid.contains(name)) {
        throw new IllegalArgumentException(
            "Unknown item category `"
                + token
                + "`. Valid categories and items: "
                + String.join(", ", valid)
                + ". Use `all` for every category.");
      }
      names.add(name);
    }
    return names.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(names));
  }

  /** The prices shared by every entry; an entry's parameter of the same name replaces one. */
  public synchronized Map<String, Double> prices() {
    ensureLoaded();
    return prices;
  }

  private void ensureLoaded() {
    if (entries == null) {
      load();
    }
  }

  private void load() {
    Content content = read();
    if (content.categories() == null || content.categories().isEmpty()) {
      throw invalid("has no categories");
    }
    if (content.entries() == null || content.entries().isEmpty()) {
      throw invalid("has no entries");
    }
    List<String> problems = new ArrayList<>();
    checkNumbers("prices", content.prices(), problems);
    if (content.prices() != null) {
      content.prices().keySet().stream()
          .filter(name -> !WorthPrices.ACCEPTED.contains(name))
          .sorted()
          .forEach(name -> problems.add("prices has unknown price '" + name + "'"));
    }
    checkNumbers("alternatives", content.alternatives(), problems);
    if (content.alternatives() != null) {
      content.alternatives().keySet().stream()
          .filter(name -> !AlternativeFinder.Settings.NAMES.contains(name))
          .sorted()
          .forEach(name -> problems.add("alternatives has unknown setting '" + name + "'"));
    }
    Set<String> keys = new HashSet<>();
    List<ItemEfficiencyEntry> checked = new ArrayList<>();
    for (Entry entry : content.entries()) {
      checked.add(checked(entry, keys, content.categories(), problems));
    }
    if (!problems.isEmpty()) {
      throw invalid(String.join("; ", problems));
    }
    prices = content.prices() == null ? Map.of() : ordered(content.prices());
    categories = ordered(content.categories());
    alternativeSettings =
        content.alternatives() == null ? Map.of() : ordered(content.alternatives());
    entries = List.copyOf(checked);
  }

  private ItemEfficiencyEntry checked(
      Entry entry, Set<String> keys, Map<String, String> categories, List<String> problems) {
    if (entry == null || entry.key() == null || entry.key().isBlank()) {
      throw invalid("has an entry without a key");
    }
    String key = entry.key();
    if (!keys.add(key)) {
      problems.add("'" + key + "' is listed twice");
    }
    if (entry.aspects() == null
        || entry.aspects().isEmpty()
        || entry.aspects().stream().anyMatch(a -> a == null || a.isBlank())) {
      problems.add("'" + key + "' lists no aspects or a blank one");
    }
    if (entry.category() == null || !categories.containsKey(entry.category())) {
      problems.add("'" + key + "' has unknown category '" + entry.category() + "'");
    }
    if (entry.items() == null || entry.items().isEmpty()) {
      problems.add("'" + key + "' lists no items");
    } else {
      checkItems(key, "item", entry.items(), problems);
    }
    if (entry.alternatives() != null) {
      checkItems(key, "alternative", entry.alternatives(), problems);
    }
    if (entry.winStats() != null) {
      for (List<String> group : entry.winStats()) {
        if (group == null || group.isEmpty()) {
          problems.add("'" + key + "' has a malformed winStats group");
        } else {
          checkItems(key, "winStats item", group, problems);
        }
      }
    }
    Map<String, Double> parameters = entry.parameters() == null ? Map.of() : entry.parameters();
    parameters.forEach(
        (name, value) -> {
          if (invalidNumber(value)) {
            problems.add("'" + key + "' parameter '" + name + "' must be a number of 0 or more");
          }
        });
    return new ItemEfficiencyEntry(
        key,
        entry.category(),
        copy(entry.items()),
        copy(entry.aspects()),
        entry.winStats() == null
            ? List.of()
            : entry.winStats().stream().map(ItemEfficiencyFile::copy).toList(),
        copy(entry.alternatives()),
        Map.copyOf(parameters));
  }

  private static void checkItems(String key, String what, List<String> items, List<String> out) {
    for (String item : items) {
      if (item == null || item.isBlank() || item.startsWith(ITEM_PREFIX)) {
        out.add("'" + key + "' has " + what + " '" + item + "': use the constants key, no item_");
      }
    }
  }

  private static void checkNumbers(String section, Map<String, Double> map, List<String> out) {
    if (map != null) {
      map.forEach(
          (name, value) -> {
            if (invalidNumber(value)) {
              out.add(section + " '" + name + "' must be a number of 0 or more");
            }
          });
    }
  }

  private static boolean invalidNumber(Double value) {
    return value == null || value.isNaN() || value.isInfinite() || value < 0;
  }

  private static <V> Map<String, V> ordered(Map<String, V> map) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(map));
  }

  private static List<String> copy(List<String> list) {
    return list == null ? List.of() : List.copyOf(list);
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
          "Item efficiency file " + source + ": cannot be read: " + e.getMessage(), e);
    }
  }

  private static InputStream open(String resource) {
    try {
      return new ClassPathResource(resource).getInputStream();
    } catch (IOException e) {
      throw new IllegalStateException("Item efficiency file " + resource + ": cannot be opened", e);
    }
  }

  private IllegalStateException invalid(String problem) {
    return new IllegalStateException("Item efficiency file " + source + ": " + problem);
  }
}
