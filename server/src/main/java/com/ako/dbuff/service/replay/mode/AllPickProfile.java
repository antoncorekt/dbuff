package com.ako.dbuff.service.replay.mode;

import org.springframework.stereotype.Component;

/** The mode the client calls All Pick, stored by OpenDota as {@code game_mode_all_draft}. */
@Component
public class AllPickProfile implements GameModeProfile {
  public static final long MODE_ID = 22;

  @Override
  public long gameModeId() {
    return MODE_ID;
  }

  @Override
  public String name() {
    return "All Pick";
  }
}
