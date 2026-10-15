package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.analysis.role.RoleNames;
import java.util.List;
import java.util.stream.Collectors;

/** The build fit findings in plain words. */
public final class BuildFitWording {
  private BuildFitWording() {}

  /**
   * What a stage's verdict says about a hero: the stated gaps, each with what backs it, or that the
   * build fit. A stage of a hero whose drafted and played roles differ is named first.
   */
  public static String verdict(String hero, BuildFitStage stage, int role, List<BuildGap> gaps) {
    String body =
        gaps.isEmpty()
            ? hero + "'s build fit the " + RoleNames.words(role) + " role."
            : gaps.stream().map(gap -> gap(hero, gap)).collect(Collectors.joining(" "));
    return switch (stage) {
      case INTENT -> "Drafted as " + RoleNames.words(role) + ": " + body;
      case FINAL -> "Played " + RoleNames.words(role) + ": " + body;
      case BOTH -> body;
    };
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
      case DIED_ALONE -> lacks + " and died alone " + times(gap.figure()) + ".";
      case LOW_NET_WORTH ->
          lacks + " and finished " + ordinal(gap.figure()) + " of 5 in team net worth.";
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

  private static String ordinal(int place) {
    return switch (place) {
      case 1 -> "1st";
      case 2 -> "2nd";
      case 3 -> "3rd";
      default -> place + "th";
    };
  }
}
