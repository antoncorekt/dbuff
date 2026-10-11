package com.ako.dbuff.service.replay.map;

import com.ako.dbuff.service.replay.MapFacts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The map layout the analyses work on, frozen as a resource because the replay that showed it will
 * be gone. A game played on another layout (a patch moved a tower) has another version and gets no
 * layout until a new one is frozen, rather than being classified on the wrong map.
 */
@Component
public class MapLayouts {
  private static final String RESOURCE = "replay/map-layout.json";

  private final MapLayout frozen;

  public MapLayouts(ObjectMapper objectMapper) {
    try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
      this.frozen =
          new MapLayout(objectMapper.readValue(in, MapFacts.class), MapLayout.Tuning.defaults());
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + RESOURCE, e);
    }
  }

  public String frozenVersion() {
    return frozen.version();
  }

  /** The layout for a game's map version, or empty when that version has not been frozen. */
  public Optional<MapLayout> forVersion(String version) {
    return frozen.version().equals(version) ? Optional.of(frozen) : Optional.empty();
  }
}
