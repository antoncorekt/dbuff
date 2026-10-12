package com.ako.dbuff.service.replay.mode;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameModeRegistryTest {

  private final ReplayProcessingProperties properties = new ReplayProcessingProperties();

  @Test
  void activeFor_findsTheProfileOfEachDefaultMode() {
    GameModeRegistry registry = TestModes.registry(properties);

    assertThat(registry.activeFor(18L)).containsSame(TestModes.ABILITY_DRAFT);
    assertThat(registry.activeFor(22L)).containsSame(TestModes.ALL_PICK);
  }

  @Test
  void activeFor_isEmptyForAModeThatIsRegisteredButNotConfigured() {
    properties.setSupportedModes(List.of(18L));

    GameModeRegistry registry = TestModes.registry(properties);

    assertThat(registry.activeFor(22L)).isEmpty();
    assertThat(registry.activeFor(18L)).isPresent();
  }

  @Test
  void activeFor_isEmptyForAModeThatIsConfiguredButHasNoProfile() {
    properties.setSupportedModes(List.of(18L, 22L, 23L));

    GameModeRegistry registry = TestModes.registry(properties);

    assertThat(registry.activeFor(23L)).isEmpty();
  }

  @Test
  void activeFor_isEmptyForNoMode() {
    assertThat(TestModes.registry(properties).activeFor(null)).isEmpty();
  }

  @Test
  void activeFor_followsTheConfigurationAsItChanges() {
    GameModeRegistry registry = TestModes.registry(properties);
    properties.setSupportedModes(List.of(22L));

    assertThat(registry.activeFor(18L)).isEmpty();
    assertThat(registry.activeFor(22L)).isPresent();
  }

  @Test
  void activeModeIds_holdsTheConfiguredModesThatHaveAProfile() {
    properties.setSupportedModes(List.of(18L, 22L, 23L));

    assertThat(TestModes.registry(properties).activeModeIds()).containsExactlyInAnyOrder(18L, 22L);
  }

  @Test
  void activeModeIds_followsTheConfigurationAsItChanges() {
    GameModeRegistry registry = TestModes.registry(properties);
    properties.setSupportedModes(List.of(22L));

    assertThat(registry.activeModeIds()).containsExactly(22L);
  }
}
