package com.ako.dbuff.service.replay.analysis.creep;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The gold and experience a creep pays, from the checked-in file {@code replay/creep-values.json}
 * (measured on the four fixture games, a first guess). Read and checked when the application
 * starts, so a typo stops it with the entry named.
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

  private final Map<CreepClass, Entry> classes = new EnumMap<>(CreepClass.class);
  private final Map<String, Entry> units = new LinkedHashMap<>();

  @Autowired
  public CreepValues(
      @Value("${replay.analysis.creep-values.resource:replay/creep-values.json}") String resource) {
    this(open(resource), resource);
  }

  CreepValues(InputStream in, String source) {
    Content content = read(in, source);
    if (content.status() == null || content.status().isBlank()) {
      throw invalid(source, "has no status saying how the values were measured");
    }
    if (content.classes() == null || content.classes().isEmpty()) {
      throw invalid(source, "has no classes");
    }
    content
        .classes()
        .forEach((name, entry) -> classes.put(classOf(source, name), checked(source, name, entry)));
    if (content.neutralUnits() != null) {
      content
          .neutralUnits()
          .forEach(
              (name, entry) -> {
                CreepClass creep = CreepClass.of(name);
                if (creep == null || !creep.isNeutral()) {
                  throw invalid(source, "'" + name + "' is no camp creep");
                }
                units.put(name, checked(source, name, entry));
              });
    }
  }

  public Optional<Entry> of(CreepClass creepClass) {
    return Optional.ofNullable(classes.get(creepClass));
  }

  /** The value measured for one camp creep by its unit name. */
  public Optional<Entry> ofUnit(String unit) {
    return Optional.ofNullable(units.get(unit));
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
      Content content =
          new ObjectMapper()
              .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
              .readValue(in, Content.class);
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
