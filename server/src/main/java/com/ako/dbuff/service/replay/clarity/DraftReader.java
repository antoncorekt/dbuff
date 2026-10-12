package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.AbilityDraftFacts;
import java.util.function.IntSupplier;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;

/**
 * Reads the draft of one game mode from the game rules entity during a parse. It returns {@link
 * AbilityDraftFacts}, the only draft shape there is; a mode with a different one would make this
 * generic along with {@code ReplayFacts.draft}.
 */
interface DraftReader {
  /**
   * Called for every update of the game rules; {@code gameTick} gives the paused-time-free tick and
   * is asked for only when something worth stamping was found.
   */
  void observe(Entity rules, FieldPath[] paths, int count, IntSupplier gameTick);

  /** The draft as it stands when the game starts, null when the replay holds none. */
  AbilityDraftFacts read(Entity rules);
}
