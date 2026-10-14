package com.ako.dbuff.service.replay.read;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

final class Sorted {
  private Sorted() {}

  @SafeVarargs
  static <T> Comparator<T> by(Function<T, ? extends Comparable<?>>... keys) {
    Comparator<T> order = (a, b) -> 0;
    for (Function<T, ? extends Comparable<?>> key : keys) {
      order = order.thenComparing(nullsLast(key));
    }
    return order;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T> Comparator<T> nullsLast(Function<T, ? extends Comparable<?>> key) {
    return Comparator.comparing(
        t -> (Comparable) key.apply(t), Comparator.nullsLast(Comparator.naturalOrder()));
  }

  static <T> List<T> of(List<T> rows, Comparator<T> order) {
    return rows.stream().sorted(order).toList();
  }
}
