package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Words one stored row in plain words, with the number of games always beside the rate. */
@Component
@RequiredArgsConstructor
public class SkillStatWording {
  /** Below this many games the count is worded as small. */
  static final int SMALL_SAMPLE = 10;

  private final SkillStatNames names;

  public String describe(SkillPickStatDomain row) {
    return subject(row) + ": " + figures(row) + lift(row);
  }

  private String subject(SkillPickStatDomain row) {
    return switch (row.getKind()) {
      case HERO -> names.hero(row.getFirstId());
      case SKILL -> names.skill(row.getFirstId());
      case HERO_SKILL -> names.hero(row.getFirstId()) + " with " + names.skill(row.getSecondId());
      case SKILL_PAIR -> names.skill(row.getFirstId()) + " with " + names.skill(row.getSecondId());
    };
  }

  private static String figures(SkillPickStatDomain row) {
    long games = row.getGames();
    if (games == 0) {
      return "no games in the last " + months(row.getWindowMonths());
    }
    String count =
        (games < SMALL_SAMPLE ? "only " : "") + games + (games == 1 ? " game" : " games");
    return Math.round(100.0 * row.getWins() / games) + "% in " + count;
  }

  private static String lift(SkillPickStatDomain row) {
    OptionalDouble lift = SkillStatMath.liftPoints(row);
    if (lift.isEmpty()) {
      return "";
    }
    long points = Math.round(lift.getAsDouble());
    if (points == 0) {
      return ", the same as what each gives alone";
    }
    return ", "
        + Math.abs(points)
        + (Math.abs(points) == 1 ? " point " : " points ")
        + (points > 0 ? "above" : "below")
        + " what each gives alone";
  }

  private static String months(Integer months) {
    int m = months == null ? 0 : months;
    return m == 1 ? "month" : m + " months";
  }
}
