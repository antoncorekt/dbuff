package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.AbilityDraftFacts;
import java.util.function.IntSupplier;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;

/** For a mode without a draft pool to read. */
final class NoDraftReader implements DraftReader {
  @Override
  public void observe(Entity rules, FieldPath[] paths, int count, IntSupplier gameTick) {}

  @Override
  public AbilityDraftFacts read(Entity rules) {
    return null;
  }
}
