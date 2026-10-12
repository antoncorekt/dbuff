package com.ako.dbuff.service.replay.mode;

/**
 * What the replay pipeline knows about one game mode. A mode is processed when it has a profile
 * bean and is listed in {@code replay.processing.supported-modes}.
 */
public interface GameModeProfile {
  /** The id OpenDota stores as {@code MatchDomain.gameModeId}. */
  long gameModeId();

  String name();
}
