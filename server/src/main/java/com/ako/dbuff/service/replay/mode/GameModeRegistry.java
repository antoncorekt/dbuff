package com.ako.dbuff.service.replay.mode;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The modes the pipeline works on: those with a registered profile that the configuration also
 * lists, so adding a mode takes one profile and one configuration entry.
 */
@Slf4j
@Component
public class GameModeRegistry {
  private final Map<Long, GameModeProfile> profiles;
  private final ReplayProcessingProperties properties;

  public GameModeRegistry(List<GameModeProfile> profiles, ReplayProcessingProperties properties) {
    this.profiles =
        profiles.stream()
            .collect(
                Collectors.toUnmodifiableMap(GameModeProfile::gameModeId, Function.identity()));
    this.properties = properties;
    properties.getSupportedModes().stream()
        .filter(Objects::nonNull)
        .filter(mode -> !this.profiles.containsKey(mode))
        .forEach(mode -> log.warn("Replay mode {} is configured but has no profile", mode));
  }

  /** The profile of the mode, empty when it is not configured or has no profile. */
  public Optional<GameModeProfile> activeFor(Long gameModeId) {
    if (gameModeId == null || !activeModeIds().contains(gameModeId)) {
      return Optional.empty();
    }
    return Optional.of(profiles.get(gameModeId));
  }

  /** The ids of the configured modes that have a profile. */
  public Set<Long> activeModeIds() {
    return properties.getSupportedModes().stream()
        .filter(Objects::nonNull)
        .filter(profiles::containsKey)
        .collect(Collectors.toUnmodifiableSet());
  }
}
