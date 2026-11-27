package com.ako.dbuff.service.replay.analysis.defense;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The defensive item catalog, from the checked-in file {@code replay/defense-items.yaml}: a list of
 * {@code {key, kind}} with keys as OpenDota names them, without the {@code item_} prefix. Read when
 * the application starts; an unknown top-level key, an unknown kind, a duplicate or prefixed key or
 * an empty list stops it with the entry named.
 */
@Component
public class DefenseItemsFile {
  private static final String LABEL = "Defense items file";
  private static final String PREFIX = "item_";

  private record Content(List<Entry> items) {}

  private record Entry(String key, String kind) {}

  private static final ObjectMapper MAPPER =
      new ObjectMapper(new YAMLFactory())
          .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  private final Map<String, DefenseItemKind> kinds = new LinkedHashMap<>();

  @Autowired
  public DefenseItemsFile(
      @Value("${replay.analysis.defense-items.resource:replay/defense-items.yaml}")
          String resource) {
    this(open(resource), resource);
  }

  DefenseItemsFile(InputStream in, String source) {
    Content content = read(in, source);
    if (content == null || content.items() == null || content.items().isEmpty()) {
      throw invalid(source, "has no items");
    }
    for (Entry entry : content.items()) {
      if (entry == null || entry.key() == null || entry.key().isBlank()) {
        throw invalid(source, "has an entry without a key");
      }
      String key = entry.key();
      if (key.startsWith(PREFIX)) {
        throw invalid(source, "'" + key + "' must be written without the " + PREFIX + " prefix");
      }
      if (kinds.containsKey(key)) {
        throw invalid(source, "'" + key + "' is listed twice");
      }
      kinds.put(key, kind(source, key, entry.kind()));
    }
  }

  /** Catalog item keys in file order, to their kind. */
  public Map<String, DefenseItemKind> kinds() {
    return Collections.unmodifiableMap(kinds);
  }

  /**
   * The kind of a state-change item name such as {@code item_pipe}, or null outside the catalog.
   */
  public DefenseItemKind kindOfItemName(String name) {
    return name != null && name.startsWith(PREFIX)
        ? kinds.get(name.substring(PREFIX.length()))
        : null;
  }

  private static DefenseItemKind kind(String source, String key, String kind) {
    try {
      return DefenseItemKind.valueOf(kind);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw invalid(source, "'" + key + "' has an unknown kind '" + kind + "'");
    }
  }

  private static Content read(InputStream in, String source) {
    try (in) {
      return MAPPER.readValue(
          in.available() == 0 ? InputStream.nullInputStream() : in, Content.class);
    } catch (MismatchedInputException e) {
      throw invalid(
          source,
          e.getMessage().startsWith("No content") ? "has no items" : e.getOriginalMessage());
    } catch (JsonProcessingException e) {
      throw invalid(source, e.getOriginalMessage());
    } catch (IOException e) {
      throw invalid(source, e.getMessage());
    }
  }

  private static IllegalStateException invalid(String source, String message) {
    return new IllegalStateException(LABEL + " " + source + ": " + message);
  }

  private static InputStream open(String resource) {
    try {
      return new ClassPathResource(resource).getInputStream();
    } catch (IOException e) {
      throw new IllegalStateException(LABEL + " " + resource + ": cannot be opened", e);
    }
  }
}
