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
  /** Whether every expected pick has been seen, so that further updates need not be looked at. */
  boolean done();

  /**
   * Called for a changed property of the game rules while the draft may still be going on; the
   * caller builds {@code name} once for all readers. {@code gameTick} gives the paused-time-free
   * tick and is asked for only when something worth stamping was found.
   */
  void observe(Entity rules, FieldPath path, String name, IntSupplier gameTick);

  /** The draft as it stands when the game starts, null when the replay holds none. */
  T read(Entity rules);
}
