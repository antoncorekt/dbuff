package com.ako.dbuff.service.replay.mode;

import org.springframework.stereotype.Component;

/** Ability Draft: each player drafts skills from a shared pool before the game. */
@Component
public class AbilityDraftProfile implements GameModeProfile {
  public static final long MODE_ID = 18;
  private static final int POOL_SIZE = 48;
  private static final int PICKS = 40;
  private static final int HERO_POOL_SIZE = 12;
  private static final int HERO_PICKS = 10;

  @Override
  public long gameModeId() {
    return MODE_ID;
  }

  @Override
  public String name() {
    return "Ability Draft";
  }

  public int poolSize() {
    return POOL_SIZE;
  }

  public int picks() {
    return PICKS;
  }

  public int heroPoolSize() {
    return HERO_POOL_SIZE;
  }

  public int heroPicks() {
    return HERO_PICKS;
  }
}
