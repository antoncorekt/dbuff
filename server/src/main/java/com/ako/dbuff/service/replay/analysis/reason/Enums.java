package com.ako.dbuff.service.replay.analysis.reason;

/** Reads the enum names the analyzers stored as text. */
public final class Enums {
  private Enums() {}

  /** Null when the text is null or names no constant, so a bad row is skipped, not fatal. */
  public static <E extends Enum<E>> E find(Class<E> type, String name) {
    if (name == null) {
      return null;
    }
    try {
      return Enum.valueOf(type, name);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
