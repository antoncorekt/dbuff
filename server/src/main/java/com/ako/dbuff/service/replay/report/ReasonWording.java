package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.service.replay.analysis.composition.CompositionToken;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import com.ako.dbuff.service.replay.analysis.rune.RuneEvidence;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * A stored fight reason as a phrase in a sentence: a player reason starts with the hero, a team
 * reason with "the team". Most evidence is an internal note (a presence name, ability names), so
 * the phrase is chosen by code and the evidence only fills in what the code leaves open.
 */
@Component
public class ReasonWording {
  private static final String TEAM = "the team";
  private static final String TEAM_REASON = "TEAM";
  private static final String ENEMY_RUNE = "ENEMY_RUNE_ADVANTAGE";
  private static final Pattern ABOUT_A_TEAM = Pattern.compile("^(Radiant|Dire) ");
  private static final Pattern INVISIBLE = Pattern.compile("^(.+?) (?:was|were) invisible");
  private static final Pattern FARMING = Pattern.compile("was (farming [a-z ]+?) when");

  private static final Map<String, BiFunction<ReasonWording, String, String>> PLAYER_PHRASES =
      Map.ofEntries(
          Map.entry("NO_TP", (w, e) -> "had no teleport"),
          Map.entry("TP_ON_COOLDOWN", (w, e) -> "had the teleport on cooldown"),
          Map.entry("HAD_TP_NOT_USED", (w, e) -> "had a teleport and did not use it"),
          Map.entry("NO_MANA", ReasonWording::outOfMana),
          Map.entry("ABILITY_ON_COOLDOWN", ReasonWording::onCooldown),
          Map.entry("ITEM_ON_COOLDOWN", ReasonWording::onCooldown),
          Map.entry("CARRY_FARMING_ELSEWHERE", (w, e) -> farming(e)),
          Map.entry("SUPPORT_ABSENT", (w, e) -> "was not in the fight"),
          Map.entry("RUNE_ADVANTAGE", ReasonWording::tookRune),
          Map.entry("DIED_FIRST", (w, e) -> "died first"),
          Map.entry("BKB_READY_UNUSED", (w, e) -> "died with Black King Bar ready"),
          Map.entry("BKB_ON_COOLDOWN", (w, e) -> "died with Black King Bar on cooldown"),
          Map.entry("BKB_EXPIRED", (w, e) -> "died as Black King Bar had just run out"),
          Map.entry("BKB_PIERCED", (w, e) -> "was disabled through Black King Bar"),
          Map.entry("DIED_IMMUNE", (w, e) -> "died through spell immunity"),
          Map.entry("DISABLE_CHAIN", (w, e) -> "was chain-disabled"),
          Map.entry("CAUGHT_OUT", (w, e) -> "was caught out"),
          Map.entry("TOWER", (w, e) -> "died to a tower"),
          Map.entry("BURST", (w, e) -> "was burst down"),
          Map.entry("OUT_OF_POSITION", (w, e) -> "stood too far in front"),
          Map.entry("NO_BKB", (w, e) -> "had no Black King Bar"),
          Map.entry("ROTATION_WASTED_BEFORE_FIGHT", (w, e) -> wastedRotation(e)));

  private static final Map<String, Function<String, String>> VISION_PHRASES =
      Map.of(
          "NO_VISION_BEFORE_FIGHT", e -> "the team had no vision near the fight, the enemy did",
          "NO_DETECTION_VS_INVISIBLE", ReasonWording::noDetection,
          "LATE_WARD", e -> "the team's ward near the fight went down too late");

  private final AbilityLabels labels;

  public ReasonWording(AbilityLabels labels) {
    this.labels = labels;
  }

  public String phrase(FightReasonDomain reason, String hero) {
    if (TEAM_REASON.equals(reason.getScope())) {
      return teamPhrase(reason.getCode(), reason.getEvidence());
    }
    String evidence = reason.getEvidence() == null ? "" : reason.getEvidence().trim();
    BiFunction<ReasonWording, String, String> known = PLAYER_PHRASES.get(reason.getCode());
    if (known != null) {
      return hero + " " + known.apply(this, evidence);
    }
    String code = reason.getCode() == null ? "" : reason.getCode();
    if (evidence.isEmpty()) {
      return hero + " " + code.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
    return evidence.startsWith(hero) ? evidence : hero + ": " + evidence;
  }

  /**
   * The phrase of a reason with its hero looked up by slot; empty for a player reason whose hero is
   * not known.
   */
  public Optional<String> phrase(FightReasonDomain reason, Map<Long, String> heroes) {
    String hero = reason.getSubjectSlot() == null ? null : heroes.get(reason.getSubjectSlot());
    if (hero == null && !TEAM_REASON.equals(reason.getScope())) {
      return Optional.empty();
    }
    return Optional.of(phrase(reason, hero));
  }

  /** The first {@code limit} ranked reasons that can be worded, joined with commas. */
  public String ranked(List<RankedReason> reasons, Map<Long, String> heroes, int limit) {
    return reasons.stream()
        .filter(RankedReason::ranked)
        .map(r -> phrase(r.reason(), heroes))
        .flatMap(Optional::stream)
        .limit(limit)
        .collect(Collectors.joining(", "));
  }

  private String teamPhrase(String code, String evidence) {
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
    Function<String, String> vision = code == null ? null : VISION_PHRASES.get(code);
    if (vision != null) {
      return vision.apply(evidence == null ? "" : evidence.trim());
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

  private String tookRune(String evidence) {
    List<RuneEvidence> runes = RuneEvidence.parseAll(evidence);
    if (runes.isEmpty()) {
      return "had a rune effect";
    }
    return "took "
        + runes.stream()
            .map(r -> "%s %s rune %s".formatted(article(r.rune()), r.rune(), r.timing()))
            .collect(Collectors.joining(" and "));
  }

  private static String wastedRotation(String evidence) {
    return evidence.contains("he died on it")
        ? "died on a rotation just before the fight"
        : "had just wasted a rotation and missed the fight";
  }

  private static String noDetection(String evidence) {
    Matcher m = INVISIBLE.matcher(evidence);
    String who = m.find() ? m.group(1) : "an enemy hero";
    return "the team had no detection against "
        + (who.startsWith("An enemy") ? "an enemy hero" : who);
  }

  private static String article(String word) {
    return "aeiou".indexOf(word.charAt(0)) >= 0 ? "an" : "a";
  }

  private String outOfMana(String evidence) {
    String names = abilities(evidence);
    return names.isEmpty() ? "ran out of mana" : "was out of mana for " + names;
  }

  private String onCooldown(String evidence) {
    String names = abilities(evidence);
    return names.isEmpty() ? "had an ability on cooldown" : "had " + names + " on cooldown";
  }

  private String abilities(String evidence) {
    return Arrays.stream(evidence.split(","))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .map(labels::label)
        .collect(Collectors.joining(" and "));
  }

  private static String farming(String evidence) {
    Matcher m = FARMING.matcher(evidence);
    return "was " + (m.find() ? m.group(1) : "farming elsewhere");
  }
}
