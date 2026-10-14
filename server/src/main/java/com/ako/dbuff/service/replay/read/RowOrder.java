package com.ako.dbuff.service.replay.read;

import java.util.Comparator;
import java.util.function.Function;

/** Typed comparator keys for stored rows, whose columns may be null. */
final class RowOrder {
  private RowOrder() {}

  static <T, U extends Comparable<? super U>> Comparator<T> nullsLast(
      Function<? super T, ? extends U> key) {
    return Comparator.comparing(key, Comparator.nullsLast(Comparator.<U>naturalOrder()));
  }
}
