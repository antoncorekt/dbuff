package com.ako.dbuff.service.replay.analysis.item;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The items and item groups to analyse, from the checked-in file {@code
 * replay/item-efficiency.json}. Read and checked on first use, so a bad file stops that use with
 * the entry named and never the application start. Adding an item is a new entry for an existing
 * kind.
 */
@Component
public class ItemEfficiencyFile {
  private record Content(String status, List<Entry> entries) {}

  private record Entry(
      String key, String kind, List<String> items, Map<String, Double> parameters) {}

  private static final String ITEM_PREFIX = "item_";
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  private final Supplier<InputStream> input;
  private final String source;
  private List<ItemEfficiencyEntry> entries;

  @Autowired
  public ItemEfficiencyFile(
      @Value("${replay.analysis.item-efficiency.resource:replay/item-efficiency.json}")
          String resource) {
    this(() -> open(resource), resource);
  }

  ItemEfficiencyFile(Supplier<InputStream> input, String source) {
    this.input = input;
    this.source = source;
  }

  public synchronized List<ItemEfficiencyEntry> entries() {
    if (entries == null) {
      entries = load();
    }
    return entries;
  }

  private List<ItemEfficiencyEntry> load() {
    Content content = read();
    if (content.status() == null || content.status().isBlank()) {
      throw invalid("has no status saying how the parameters were chosen");
    }
    if (content.entries() == null || content.entries().isEmpty()) {
      throw invalid("has no entries");
    }
    Set<String> keys = new HashSet<>();
    return content.entries().stream().map(entry -> checked(entry, keys)).toList();
  }

  private ItemEfficiencyEntry checked(Entry entry, Set<String> keys) {
    if (entry == null || entry.key() == null || entry.key().isBlank()) {
      throw invalid("has an entry without a key");
    }
    String key = entry.key();
    if (!keys.add(key)) {
      throw invalid("'" + key + "' is listed twice");
    }
    if (entry.kind() == null || entry.kind().isBlank()) {
      throw invalid("'" + key + "' has no kind");
    }
    if (entry.items() == null || entry.items().isEmpty()) {
      throw invalid("'" + key + "' lists no items");
    }
    for (String item : entry.items()) {
      if (item == null || item.isBlank() || item.startsWith(ITEM_PREFIX)) {
        throw invalid("'" + key + "' has item '" + item + "': use the constants key, no item_");
      }
    }
    Map<String, Double> parameters = entry.parameters() == null ? Map.of() : entry.parameters();
    parameters.forEach(
        (name, value) -> {
          if (value == null || value.isNaN() || value.isInfinite() || value < 0) {
            throw invalid("'" + key + "' parameter '" + name + "' must be a number of 0 or more");
          }
        });
    return new ItemEfficiencyEntry(
        key, entry.kind(), List.copyOf(entry.items()), Map.copyOf(parameters));
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
