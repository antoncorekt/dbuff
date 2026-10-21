package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.analysis.role.RoleNames;
import java.util.List;
import java.util.stream.Collectors;

/** The build fit findings in plain words. */
public final class BuildFitWording {
  private BuildFitWording() {}

  /**
   * What a stage's verdict says about a hero: the stated gaps, each with what backs it; or the
   * lacks the match did not show; or that the build fit. Functions the build has no value for are
   * named last. A stage of a hero whose drafted and played roles differ is named first.
   */
  public static String verdict(
      String hero, BuildFitStage stage, int role, BuildFitJudge.Verdict verdict) {
    String body =
        switch (verdict.level()) {
          case FIT ->
              verdict.unknown().isEmpty()
                  ? hero + "'s build fit the " + RoleNames.words(role) + " role."
                  : hero + "'s build showed no gap in the " + RoleNames.words(role) + " role.";
          case GAPS_NOT_SHOWN ->
              hero
                  + "'s build lacks "
                  + list(verdict.missing())
                  + ", but the match did not show it.";
          case GAP ->
              verdict.gaps().stream().map(gap -> gap(hero, gap)).collect(Collectors.joining(" "));
        };
    if (!verdict.unknown().isEmpty()) {
      body += " " + capitalized(list(verdict.unknown())) + " could not be measured.";
    }
    return switch (stage) {
      case INTENT -> "Drafted as " + RoleNames.words(role) + ": " + body;
      case FINAL -> "Played " + RoleNames.words(role) + ": " + body;
      case BOTH -> body;
    };
  }

  private static String list(List<RoleFunction> functions) {
    List<String> words = functions.stream().map(RoleFunction::words).toList();
    if (words.size() == 1) {
      return words.get(0);
    }
    return String.join(", ", words.subList(0, words.size() - 1))
        + " and "
        + words.get(words.size() - 1);
  }

  private static String capitalized(String text) {
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  /** "Pudge was drafted as a roaming support and played carry." */
  public static String roleChange(String hero, int drafted, int played) {
    return hero
        + " was drafted as "
        + RoleNames.words(drafted)
        + " and played "
        + RoleNames.words(played)
        + ".";
  }

  private static String gap(String hero, BuildGap gap) {
    String lacks = hero + " had " + lack(gap);
    return switch (gap.evidence()) {
      case DIED_ALONE -> lacks + " and died alone " + times((int) gap.figure()) + ".";
      case LOW_NET_WORTH ->
          lacks + " and finished " + place(gap.figure()) + " of 5 in team net worth.";
    };
  }

  private static String lack(BuildGap gap) {
    RoleFunction function = gap.function();
    if (function == RoleFunction.FARM_PRIORITY) {
      return gap.none() ? "no farming skill" : "only weak farming skills";
    }
    return (gap.none() ? "no " : "only a weak ") + function.words();
  }

  private static String times(int count) {
    return switch (count) {
      case 1 -> "once";
      case 2 -> "twice";
      default -> count + " times";
    };
  }

  private static String place(double place) {
    int whole = (int) Math.floor(place);
    return place == whole
        ? ordinal(whole)
        : "tied " + ordinal(whole) + " and " + ordinal(whole + 1);
  }

  private static String ordinal(int place) {
    return switch (place) {
      case 1 -> "1st";
      case 2 -> "2nd";
      case 3 -> "3rd";
      default -> place + "th";
    };
  }
}
