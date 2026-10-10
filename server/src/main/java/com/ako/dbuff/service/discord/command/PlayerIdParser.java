package com.ako.dbuff.service.discord.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Dota account ID out of the forms a person actually has to hand: the 32-bit account ID the
 * autocomplete submits, a Steam64 ID, or a profile link from OpenDota, Dotabuff, Stratz or Steam.
 *
 * <p>Vanity Steam URLs and persona names are deliberately not accepted: neither identifies a single
 * account without a lookup, and guessing would track the wrong player.
 */
public final class PlayerIdParser {

  /** Steam64 IDs are the 32-bit account ID offset by this base. */
  private static final long STEAM64_BASE = 76561197960265728L;

  /** Account IDs are unsigned 32-bit. */
  private static final long MAX_ACCOUNT_ID = 0xFFFFFFFFL;

  private static final Pattern PROFILE_LINK = Pattern.compile("/(?:players|profiles)/(\\d+)");

  private PlayerIdParser() {}

  /**
   * The outcome of parsing several references.
   *
   * @param ids account IDs in the order given, without duplicates
   * @param invalid entries that were not understood, never null
   */
  public record Result(Set<Long> ids, List<String> invalid) {}

  public static Result parseAll(List<String> references) {
    Set<Long> ids = new LinkedHashSet<>();
    List<String> invalid = new java.util.ArrayList<>();
    for (String reference : references) {
      if (reference == null || reference.isBlank()) {
        continue;
      }
      parse(reference).ifPresentOrElse(ids::add, () -> invalid.add(reference.trim()));
    }
    return new Result(ids, invalid);
  }

  /** The account ID for one reference, or empty when it is not a recognised form. */
  public static Optional<Long> parse(String reference) {
    String trimmed = reference.trim();
    if (trimmed.chars().allMatch(Character::isDigit) && !trimmed.isEmpty()) {
      return fromNumber(trimmed);
    }
    Matcher link = PROFILE_LINK.matcher(trimmed);
    return link.find() ? fromNumber(link.group(1)) : Optional.empty();
  }

  private static Optional<Long> fromNumber(String digits) {
    // A 64-bit value does not fit a long past 18 digits; no real ID is that long.
    if (digits.length() > 18) {
      return Optional.empty();
    }
    long value = Long.parseLong(digits);
    long accountId = value >= STEAM64_BASE ? value - STEAM64_BASE : value;
    return accountId > 0 && accountId <= MAX_ACCOUNT_ID ? Optional.of(accountId) : Optional.empty();
  }
}
