package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.service.replay.analysis.composition.CompositionToken;
import com.ako.dbuff.service.replay.analysis.rune.RuneEvidence;
import java.util.Arrays;
import java.util.List;
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
public final class ReasonWording {
  private static final String TEAM = "the team";
  private static final String TEAM_REASON = "TEAM";
  private static final String ENEMY_RUNE = "ENEMY_RUNE_ADVANTAGE";
  private static final Pattern ABOUT_A_TEAM = Pattern.compile("^(Radiant|Dire) ");
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
          Map.entry("RUNE_ADVANTAGE", ReasonWording::tookRune),
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

  public static String phrase(FightReasonDomain reason, String hero) {
    if (TEAM_REASON.equals(reason.getScope())) {
      return teamPhrase(reason.getCode(), reason.getEvidence());
    }
    String evidence = reason.getEvidence() == null ? "" : reason.getEvidence().trim();
    Function<String, String> known = PLAYER_PHRASES.get(reason.getCode());
    if (known != null) {
      return hero + " " + known.apply(evidence);
    }
    String code = reason.getCode() == null ? "" : reason.getCode();
    if (evidence.isEmpty()) {
      return hero + " " + code.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
    return evidence.startsWith(hero) ? evidence : hero + ": " + evidence;
  }

  private static String teamPhrase(String code, String evidence) {
    if (ENEMY_RUNE.equals(code)) {
      List<RuneEvidence> runes = RuneEvidence.parseAll(evidence);
      return runes.isEmpty()
          ? TEAM + ": the enemy had a rune effect"
          : "the enemy "
              + runes.stream()
                  .map(
                      r ->
                          "%s took %s %s rune %s"
                              .formatted(r.hero(), article(r.rune()), r.rune(), r.timing()))
                  .collect(Collectors.joining(", "));
    }
    if (!CompositionToken.isToken(code)) {
      String note = evidence == null ? "" : evidence.trim();
      if (note.isEmpty()) {
        return TEAM + " had a gap";
      }
      return ABOUT_A_TEAM.matcher(note).find() ? note : TEAM + ": " + note;
    }
    String words = CompositionToken.words(code);
    boolean has = words.startsWith("no ") || Character.isDigit(words.charAt(0));
    return TEAM + (has ? " had " : ": ") + words;
  }

  private static String tookRune(String evidence) {
    List<RuneEvidence> runes = RuneEvidence.parseAll(evidence);
    if (runes.isEmpty()) {
      return "had a rune effect";
    }
    return "took "
        + runes.stream()
            .map(r -> "%s %s rune %s".formatted(article(r.rune()), r.rune(), r.timing()))
            .collect(Collectors.joining(" and "));
  }

  private static String article(String word) {
    return "aeiou".indexOf(word.charAt(0)) >= 0 ? "an" : "a";
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
