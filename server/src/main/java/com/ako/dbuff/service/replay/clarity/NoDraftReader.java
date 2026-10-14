package com.ako.dbuff.service.replay.clarity;

import java.util.function.IntSupplier;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;

/** For a mode without a draft part to read. */
final class NoDraftReader<T> implements DraftReader<T> {
  @Override
  public void observe(Entity rules, FieldPath[] paths, int count, IntSupplier gameTick) {}

  @Override
  public T read(Entity rules) {
    return null;
  }
}
