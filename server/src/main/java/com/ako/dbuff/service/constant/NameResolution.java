package com.ako.dbuff.service.constant;

import java.util.Set;

/**
 * The outcome of resolving user-supplied constant names to numeric IDs.
 *
 * <p>Deliberately returns unresolved names alongside the resolved IDs so that callers must decide
 * what to do about them. The previous behaviour silently discarded unknown names, which turned a
 * filtered query into an unfiltered one and reported the wrong statistics without any error.
 *
 * @param resolvedIds IDs successfully resolved; never null, possibly empty
 * @param unresolvedNames names that matched no constant; never null, possibly empty
 */
public record NameResolution(Set<Long> resolvedIds, Set<String> unresolvedNames) {

  public static NameResolution empty() {
    return new NameResolution(Set.of(), Set.of());
  }

  public boolean hasUnresolved() {
    return !unresolvedNames.isEmpty();
  }

  /**
   * The resolved IDs, or null when none were requested — matching the convention the ranking
   * repositories use, where a null filter set means "no filter".
   *
   * <p>Only safe to call after checking {@link #hasUnresolved()}; otherwise an all-unknown input
   * would produce null and silently widen the query.
   *
   * @return the resolved IDs, or null when empty
   */
  public Set<Long> idsOrNullIfEmpty() {
    return resolvedIds.isEmpty() ? null : resolvedIds;
  }
}
