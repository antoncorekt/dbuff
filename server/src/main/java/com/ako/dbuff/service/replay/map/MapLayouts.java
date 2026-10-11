package com.ako.dbuff.service.replay.map;

import com.ako.dbuff.service.replay.MapFacts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The map layout the analyses work on, frozen as a resource because the replay that showed it will
 * be gone. A game played on another layout (a patch moved a tower) has another version and gets no
 * layout until a new one is frozen, rather than being classified on the wrong map.
 */
@Slf4j
@Component
public class MapLayouts {
  private static final String RESOURCE = "replay/map-layout.json";

  private final MapLayout frozen;

  @Autowired
  public MapLayouts(ObjectMapper objectMapper) {
    this(objectMapper, RESOURCE);
  }

  /**
   * A layout that cannot be read leaves the frozen one empty and says so, so that one bad resource
   * costs the map analyses and not the start of the whole server.
   */
  MapLayouts(ObjectMapper objectMapper, String resource) {
    MapLayout layout = null;
    try (InputStream in = new ClassPathResource(resource).getInputStream()) {
      layout =
          new MapLayout(objectMapper.readValue(in, MapFacts.class), MapLayout.Tuning.defaults());
    } catch (IOException | RuntimeException e) {
      log.error("Cannot use the frozen map layout {}: {}", resource, e.getMessage());
    }
    this.frozen = layout;
  }

  /** Null when the frozen layout could not be loaded. */
  public String frozenVersion() {
    return frozen == null ? null : frozen.version();
  }

  /** The layout for a game's map version, or empty when that version has not been frozen. */
  public Optional<MapLayout> forVersion(String version) {
    return frozen != null && frozen.version().equals(version)
        ? Optional.of(frozen)
        : Optional.empty();
  }
}
