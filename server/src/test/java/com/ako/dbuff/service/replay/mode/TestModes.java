package com.ako.dbuff.service.replay.mode;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.util.List;

/** The two shipped profiles and a registry over them, as the default configuration builds it. */
public final class TestModes {
  public static final GameModeProfile ABILITY_DRAFT = new AbilityDraftProfile();
  public static final GameModeProfile ALL_PICK = new AllPickProfile();

  private TestModes() {}

  public static GameModeRegistry registry() {
    return registry(new ReplayProcessingProperties());
  }

  public static GameModeRegistry registry(ReplayProcessingProperties properties) {
    return new GameModeRegistry(List.of(ABILITY_DRAFT, ALL_PICK), properties);
  }
}
