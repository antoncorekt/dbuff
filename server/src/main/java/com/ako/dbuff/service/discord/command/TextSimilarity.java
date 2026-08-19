package com.ako.dbuff.service.discord.command;

import java.util.Collection;
import java.util.Optional;

/** Levenshtein distance and nearest-match lookup, for "did you mean" suggestions. */
public final class TextSimilarity {

  private TextSimilarity() {}

  /**
   * The nearest candidate to {@code input}, or empty when nothing is within {@code maxDistance}.
   *
   * <p>Comparison is case-insensitive; the returned string preserves the candidate's original
   * casing.
   *
   * @param input what the user typed
   * @param candidates the known values to match against
   * @param maxDistance beyond this, a suggestion is noise rather than help
   * @return the nearest candidate, if close enough
   */
  public static Optional<String> closest(
      String input, Collection<String> candidates, int maxDistance) {
    if (input == null || input.isBlank() || candidates == null || candidates.isEmpty()) {
      return Optional.empty();
    }
    String needle = input.trim().toLowerCase();

    String best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (String candidate : candidates) {
      if (candidate == null) {
        continue;
      }
      int distance = editDistance(needle, candidate.toLowerCase());
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    return bestDistance <= maxDistance ? Optional.ofNullable(best) : Optional.empty();
  }

  /**
   * Standard Levenshtein distance, two-row variant.
   *
   * @param a first string
   * @param b second string
   * @return the edit distance
   */
  public static int editDistance(String a, String b) {
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];

    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }

    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
        current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), substitution);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }
}
