package com.ako.dbuff.service.constant;

import java.util.Set;

/**
 * A resolved game mode filter.
 *
 * <p>{@code ids} empty with nothing unresolved means "every mode" — the repositories read an empty
 * or null ID set as "no filter". That is why {@link #isAllModes()} exists rather than callers
 * checking {@code ids.isEmpty()} themselves: the same emptiness also arises from a request that
 * resolved to nothing, and the two must not be confused.
 *
 * @param canonicalNames the constant names the request resolved to, e.g. {@code
 *     game_mode_ability_draft}. What handlers forward to the services, so the default applied at
 *     the command layer travels with the request instead of being re-derived.
 * @param ids the numeric mode IDs matching {@code MatchDomain.gameModeId}
 * @param unresolvedNames anything that matched no known mode; never silently dropped
 * @param label human-readable summary for an embed footer, e.g. {@code Ability Draft}
 */
public record GameModeSelection(
    Set<String> canonicalNames, Set<Long> ids, Set<String> unresolvedNames, String label) {

  /** Label used when no mode filter applies. */
  public static final String ALL_MODES_LABEL = "All modes";

  public static GameModeSelection allModes() {
    return new GameModeSelection(Set.of(), Set.of(), Set.of(), ALL_MODES_LABEL);
  }

  public boolean hasUnresolved() {
    return !unresolvedNames.isEmpty();
  }

  /** True when no mode restriction should be applied. */
  public boolean isAllModes() {
    return ids.isEmpty() && unresolvedNames.isEmpty();
  }

  /** The ID set to hand a repository, or null for "no filter". */
  public Set<Long> idsOrNullIfEmpty() {
    return ids.isEmpty() ? null : ids;
  }
}
