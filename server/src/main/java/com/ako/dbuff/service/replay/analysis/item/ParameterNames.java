package com.ako.dbuff.service.replay.analysis.item;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

final class ParameterNames {

  private ParameterNames() {}

  @SafeVarargs
  static Set<String> of(Collection<String>... parts) {
    Set<String> names = new HashSet<>();
    for (Collection<String> part : parts) {
      names.addAll(part);
    }
    return Set.copyOf(names);
  }

  @SafeVarargs
  static Set<String> withPrices(Collection<String>... parts) {
    Set<String> names = new HashSet<>(WorthPrices.NAMES);
    for (Collection<String> part : parts) {
      names.addAll(part);
    }
    return Set.copyOf(names);
  }
}
