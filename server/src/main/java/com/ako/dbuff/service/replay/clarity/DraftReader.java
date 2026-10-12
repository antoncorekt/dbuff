package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.AbilityDraftFacts;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;

/** Reads the draft of one game mode from the game rules entity during a parse. */
interface DraftReader {
  /** Called for every update of the game rules; {@code gameTick} is the paused-time-free tick. */
  void observe(Entity rules, FieldPath[] paths, int count, int gameTick);

  /** The draft as it stands when the game starts, null when the replay holds none. */
  AbilityDraftFacts read(Entity rules);
}
