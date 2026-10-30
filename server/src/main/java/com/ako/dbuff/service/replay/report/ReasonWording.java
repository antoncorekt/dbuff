package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.service.discord.icon.IconText;
import com.ako.dbuff.service.replay.analysis.composition.CompositionToken;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
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
          Map.entry(
              "ROTATION_WASTED_BEFORE_FIGHT",
              (w, e) -> "had just wasted a rotation and missed the fight"),
          Map.entry(
              "ROTATION_DIED_BEFORE_FIGHT",
              (w, e) -> "died on a rotation just before the fight and missed it"));

  private static final Map<String, Function<Voice, String>> VISION_PHRASES =
      Map.of(
          "NO_VISION_BEFORE_FIGHT",
              v -> v.self() + " had no vision near the fight, " + v.other() + " did",
          "NO_DETECTION_VS_INVISIBLE",
              v -> v.self() + " had no detection against an invisible " + v.otherHero(),
          "LATE_WARD", v -> v.self() + "'s ward near the fight went down too late");

  /**
   * Who a team reason is about, from the reader's side.
   *
   * @param losing the team the reason belongs to; null when not known
   * @param yours the reader's team; null when the reader has no side
   */
  public record Perspective(TeamSide losing, TeamSide yours) {
    static final Perspective NEUTRAL = new Perspective(null, null);
  }

  private record Voice(String self, String other, String otherOwner) {
    String otherHero() {
      return other.equals("the enemy") ? "enemy hero" : "hero of " + other;
    }

    static Voice of(Perspective view, String storedTeam) {
      TeamSide losing =
          view.losing() != null ? view.losing() : Enums.find(TeamSide.class, storedTeam);
      if (losing == null) {
        return new Voice(TEAM, "the enemy", "the enemy ");
      }
      if (view.yours() == null) {
        String self = Prettify.team(losing.name());
        String other = Prettify.team(losing.opponent().name());
        return new Voice(self, other, other + "'s ");
      }
      return view.yours() == losing
          ? new Voice("your team", "the enemy", "the enemy ")
          : new Voice("the enemy team", "your team", "your ");
    }
  }

  private final AbilityLabels labels;

  public ReasonWording(AbilityLabels labels) {
    this.labels = labels;
  }

  public String phrase(FightReasonDomain reason, String hero) {
    return phrase(reason, hero, Perspective.NEUTRAL);
  }

  public String phrase(FightReasonDomain reason, String hero, Perspective view) {
    if (TEAM_REASON.equals(reason.getScope())) {
      return teamPhrase(reason, Voice.of(view, reason.getTeam()));
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
    String plainHero = IconText.withoutLeadingIcon(hero);
    return evidence.startsWith(plainHero)
        ? hero + evidence.substring(plainHero.length())
        : hero + ": " + evidence;
  }

  /**
   * The phrase of a reason with its hero looked up by slot; empty for a player reason whose hero is
   * not known.
   */
  public Optional<String> phrase(FightReasonDomain reason, Map<Long, String> heroes) {
    return phrase(reason, heroes, Perspective.NEUTRAL);
  }

  private Optional<String> phrase(
      FightReasonDomain reason, Map<Long, String> heroes, Perspective view) {
    String hero = reason.getSubjectSlot() == null ? null : heroes.get(reason.getSubjectSlot());
    if (hero == null && !TEAM_REASON.equals(reason.getScope())) {
      return Optional.empty();
    }
    return Optional.of(phrase(reason, hero, view));
  }

  /** The first {@code limit} ranked reasons that can be worded, joined with commas. */
  public String ranked(List<RankedReason> reasons, Map<Long, String> heroes, int limit) {
    return ranked(reasons, heroes, limit, Perspective.NEUTRAL);
  }

  /** As {@link #ranked(List, Map, int)}, with the team reasons phrased from the reader's side. */
  public String ranked(
      List<RankedReason> reasons, Map<Long, String> heroes, int limit, Perspective view) {
    return reasons.stream()
        .filter(RankedReason::ranked)
        .map(r -> phrase(r.reason(), heroes, view))
        .flatMap(Optional::stream)
        .limit(limit)
        .collect(Collectors.joining(", "));
  }

  private String teamPhrase(FightReasonDomain reason, Voice voice) {
    String code = reason.getCode();
    String evidence = reason.getEvidence();
    if (ENEMY_RUNE.equals(code)) {
      List<RuneEvidence> runes = RuneEvidence.parseAll(evidence);
      return runes.isEmpty()
          ? voice.self() + ": " + voice.other() + " had a rune effect"
          : voice.otherOwner()
              + runes.stream()
                  .map(
                      r ->
                          "%s took %s %s rune %s"
                              .formatted(r.hero(), article(r.rune()), r.rune(), r.timing()))
                  .collect(Collectors.joining(", "));
    }
    Function<Voice, String> vision = code == null ? null : VISION_PHRASES.get(code);
    if (vision != null) {
      return vision.apply(voice);
    }
    if (!CompositionToken.isToken(code)) {
      String note = evidence == null ? "" : evidence.trim();
      if (note.isEmpty()) {
        return voice.self() + " had a gap";
      }
      return ABOUT_A_TEAM.matcher(note).find() ? note : voice.self() + ": " + note;
    }
    String words = CompositionToken.words(code);
    boolean has = words.startsWith("no ") || Character.isDigit(words.charAt(0));
    return voice.self() + (has ? " had " : ": ") + words;
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
        .map(labels::decorated)
        .collect(Collectors.joining(" and "));
  }

  private static String farming(String evidence) {
    Matcher m = FARMING.matcher(evidence);
    return "was " + (m.find() ? m.group(1) : "farming elsewhere");
  }
}
