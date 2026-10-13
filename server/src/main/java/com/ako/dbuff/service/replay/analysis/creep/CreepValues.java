package com.ako.dbuff.service.replay.analysis.creep;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The gold and experience a creep pays, from the checked-in file {@code replay/creep-values.json}
 * (measured on the four fixture games, a first guess). Read and checked on first use, so a typo
 * stops that use with the entry named and never the application start.
 */
@Component
public class CreepValues {

  /**
   * @param gold the median gold the killer received
   * @param xp the median experience for the only hero receiving it; null when never seen alone
   * @param bonusGold what a flagbearer pays the killer on top; null for other creeps
   * @param goldSamples how many kills the gold was measured on
   * @param xpSamples how many kills the experience was measured on
   */
  public record Entry(
      int gold, Integer xp, Integer bonusGold, int goldSamples, Integer xpSamples) {}

  private record Content(
      String status, Map<String, Entry> classes, Map<String, Entry> neutralUnits) {}

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final Supplier<InputStream> input;
  private final String source;
  private Map<CreepClass, Entry> classes;
  private Map<String, Entry> units;

  @Autowired
  public CreepValues(
      @Value("${replay.analysis.creep-values.resource:replay/creep-values.json}") String resource) {
    this(() -> open(resource), resource);
  }

  CreepValues(Supplier<InputStream> input, String source) {
    this.input = input;
    this.source = source;
  }

  public Optional<Entry> of(CreepClass creepClass) {
    return Optional.ofNullable(loaded().classes.get(creepClass));
  }

  /** The value measured for one camp creep by its unit name. */
  public Optional<Entry> ofUnit(String unit) {
    return Optional.ofNullable(loaded().units.get(unit));
  }

  /** Read and checked on first use, so a bad file cannot stop the application from starting. */
  private synchronized CreepValues loaded() {
    if (classes == null) {
      load();
    }
    return this;
  }

  private void load() {
    Content content = read(input.get(), source);
    if (content.status() == null || content.status().isBlank()) {
      throw invalid(source, "has no status saying how the values were measured");
    }
    if (content.classes() == null || content.classes().isEmpty()) {
      throw invalid(source, "has no classes");
    }
    Map<CreepClass, Entry> readClasses = new EnumMap<>(CreepClass.class);
    content
        .classes()
        .forEach(
            (name, entry) -> readClasses.put(classOf(source, name), checked(source, name, entry)));
    Map<String, Entry> readUnits = new LinkedHashMap<>();
    if (content.neutralUnits() != null) {
      content
          .neutralUnits()
          .forEach(
              (name, entry) -> {
                CreepClass creep = CreepClass.of(name);
                if (creep == null || !creep.isNeutral()) {
                  throw invalid(source, "'" + name + "' is no camp creep");
                }
                readUnits.put(name, checked(source, name, entry));
              });
    }
    units = readUnits;
    classes = readClasses;
  }

  private static CreepClass classOf(String source, String name) {
    for (CreepClass creepClass : CreepClass.values()) {
      if (creepClass.name().equals(name)) {
        return creepClass;
      }
    }
    throw invalid(source, "'" + name + "' is no creep class");
  }

  private static Entry checked(String source, String name, Entry entry) {
    if (entry == null) {
      throw invalid(source, "'" + name + "' is empty");
    }
    if (entry.gold() < 0 || entry.goldSamples() <= 0) {
      throw invalid(source, "'" + name + "' needs gold of 0 or more and a sample count above 0");
    }
    if ((entry.xp() == null) != (entry.xpSamples() == null)
        || (entry.xp() != null && (entry.xp() < 0 || entry.xpSamples() <= 0))) {
      throw invalid(source, "'" + name + "' needs xp and xpSamples together, xp 0 or more");
    }
    if (entry.bonusGold() != null && entry.bonusGold() < 0) {
      throw invalid(source, "'" + name + "' has a negative bonusGold");
    }
    return entry;
  }

  private static Content read(InputStream in, String source) {
    try (in) {
      Content content = MAPPER.readValue(in, Content.class);
      if (content == null) {
        throw invalid(source, "is empty");
      }
      return content;
    } catch (IOException e) {
      throw new IllegalStateException(
          "Creep value file " + source + ": cannot be read: " + e.getMessage(), e);
    }
  }

  private static InputStream open(String resource) {
    try {
      return new ClassPathResource(resource).getInputStream();
    } catch (IOException e) {
      throw new IllegalStateException("Creep value file " + resource + ": cannot be opened", e);
    }
  }

  private static IllegalStateException invalid(String source, String problem) {
    return new IllegalStateException("Creep value file " + source + ": " + problem);
  }
}
