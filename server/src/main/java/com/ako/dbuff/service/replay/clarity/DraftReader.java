package com.ako.dbuff.service.replay.clarity;

import java.util.function.IntSupplier;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;

/**
 * Reads one part of the draft of a game mode (its skills, its heroes) from the game rules entity
 * during a parse.
 *
 * @param <T> the facts the reader produces
 */
interface DraftReader<T> {
  /**
   * Called for every update of the game rules; {@code gameTick} gives the paused-time-free tick and
   * is asked for only when something worth stamping was found.
   */
  void observe(Entity rules, FieldPath[] paths, int count, IntSupplier gameTick);

  /** The draft as it stands when the game starts, null when the replay holds none. */
  T read(Entity rules);
}
