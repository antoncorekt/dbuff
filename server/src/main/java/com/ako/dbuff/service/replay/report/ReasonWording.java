package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.service.replay.analysis.composition.CompositionToken;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A stored fight reason as a phrase in a sentence: a player reason starts with the hero, a team
 * reason with "the team". Most evidence is an internal note (a presence name, ability names), so
 * the phrase is chosen by code and the evidence only fills in what the code leaves open.
 */
final class ReasonWording {
  private static final String TEAM = "the team";
  private static final String TEAM_REASON = "TEAM";
  private static final Pattern FARMING = Pattern.compile("was (farming [a-z ]+?) when");

  private static final Map<String, Function<String, String>> PLAYER_PHRASES =
      Map.ofEntries(
          Map.entry("NO_TP", e -> "had no teleport"),
          Map.entry("TP_ON_COOLDOWN", e -> "had the teleport on cooldown"),
          Map.entry("HAD_TP_NOT_USED", e -> "had a teleport and did not use it"),
          Map.entry("NO_MANA", ReasonWording::outOfMana),
          Map.entry("ABILITY_ON_COOLDOWN", ReasonWording::onCooldown),
          Map.entry("ITEM_ON_COOLDOWN", ReasonWording::onCooldown),
          Map.entry("CARRY_FARMING_ELSEWHERE", ReasonWording::farming),
          Map.entry("SUPPORT_ABSENT", e -> "was not in the fight"),
          Map.entry("DIED_FIRST", e -> "died first"),
          Map.entry("BKB_READY_UNUSED", e -> "died with Black King Bar ready"),
          Map.entry("BKB_ON_COOLDOWN", e -> "died with Black King Bar on cooldown"),
          Map.entry("BKB_EXPIRED", e -> "died as Black King Bar had just run out"),
          Map.entry("BKB_PIERCED", e -> "was disabled through Black King Bar"),
          Map.entry("DIED_IMMUNE", e -> "died through spell immunity"),
          Map.entry("DISABLE_CHAIN", e -> "was chain-disabled"),
          Map.entry("CAUGHT_OUT", e -> "was caught out"),
          Map.entry("TOWER", e -> "died to a tower"),
          Map.entry("BURST", e -> "was burst down"),
          Map.entry("OUT_OF_POSITION", e -> "stood too far in front"),
          Map.entry("NO_BKB", e -> "had no Black King Bar"));

  private ReasonWording() {}

  static String phrase(FightReasonDomain reason, String hero) {
    if (TEAM_REASON.equals(reason.getScope())) {
      return TEAM + teamGap(reason.getCode());
    }
    String evidence = reason.getEvidence() == null ? "" : reason.getEvidence().trim();
    Function<String, String> known = PLAYER_PHRASES.get(reason.getCode());
    if (known != null) {
      return hero + " " + known.apply(evidence);
    }
    String code = reason.getCode() == null ? "" : reason.getCode();
    return evidence.isEmpty()
        ? hero + " " + code.toLowerCase(Locale.ROOT).replace('_', ' ')
        : hero + ": " + evidence;
  }

  private static String teamGap(String code) {
    String words = code == null ? "" : CompositionToken.words(code);
    if (words.isBlank()) {
      return " had a gap";
    }
    boolean has = words.startsWith("no ") || Character.isDigit(words.charAt(0));
    return has ? " had " + words : ": " + words;
  }

  private static String outOfMana(String evidence) {
    String names = abilities(evidence);
    return names.isEmpty() ? "ran out of mana" : "was out of mana for " + names;
  }

  private static String onCooldown(String evidence) {
    String names = abilities(evidence);
    return names.isEmpty() ? "had an ability on cooldown" : "had " + names + " on cooldown";
  }

  private static String abilities(String evidence) {
    return Arrays.stream(evidence.split(","))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .map(Prettify::ability)
        .collect(Collectors.joining(" and "));
  }

  private static String farming(String evidence) {
    Matcher m = FARMING.matcher(evidence);
    return "was " + (m.find() ? m.group(1) : "farming elsewhere");
  }
}
