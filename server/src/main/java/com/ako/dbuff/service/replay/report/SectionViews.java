package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * What the sections share when they build a {@link SectionView}: the verdict with its accent, the
 * accent for a result of the tracked team, counts in plain words and the tracked team's rows. A
 * section's plain text is its {@code lines()}; none of this touches it.
 */
final class SectionViews {
  private SectionViews() {}

  /** A one-line answer and the colour that goes with it. */
  record Verdict(String text, Accent accent) {}

  /** The rows of the tracked team when it has any, otherwise all rows. */
  record Scope<T>(List<T> rows, boolean ownTeam) {}

  static <T> Scope<T> scope(List<T> rows, Function<T, String> team, TeamSide yours) {
    if (yours != null) {
      List<T> own =
          rows.stream().filter(r -> Enums.find(TeamSide.class, team.apply(r)) == yours).toList();
      if (!own.isEmpty()) {
        return new Scope<>(own, true);
      }
    }
    return new Scope<>(rows, false);
  }

  /**
   * The view of a section whose {@code lines} start with a bold title: that line becomes the title,
   * the rest the bullets.
   */
  static Optional<SectionView> titled(List<String> lines, Verdict verdict) {
    if (lines.isEmpty()) {
      return Optional.empty();
    }
    SectionView base = SectionView.ofLines(lines);
    return Optional.of(
        new SectionView(base.title(), verdict.text(), verdict.accent(), base.bullets(), null));
  }

  /** Green when the tracked team won, red when it lost, grey when there is no tracked team. */
  static Accent forTeam(TeamSide winner, TeamSide yours) {
    if (yours == null || winner == null) {
      return Accent.NEUTRAL;
    }
    return winner == yours ? Accent.WIN : Accent.LOSS;
  }

  /** Green when good outweighs bad, red when bad outweighs good, grey when they are level. */
  static Accent balance(long good, long bad) {
    return good > bad ? Accent.WIN : good < bad ? Accent.LOSS : Accent.NEUTRAL;
  }

  /** Who won more of {@code total} things, from the tracked team's side when there is one. */
  static Verdict contest(TeamSide yours, long radiant, long dire, long total, String things) {
    if (yours != null) {
      long won = yours == TeamSide.RADIANT ? radiant : dire;
      long lost = yours == TeamSide.RADIANT ? dire : radiant;
      String team = Prettify.teamOf(yours.name(), yours);
      String text =
          won == lost
              ? String.format(Locale.ROOT, "%s split %d to %d in %s", team, won, lost, things)
              : String.format(Locale.ROOT, "%s won %d of %d %s", team, won, total, things);
      return new Verdict(text, balance(won, lost));
    }
    String text =
        radiant == dire
            ? String.format(Locale.ROOT, "Radiant and Dire won %d each", radiant)
            : String.format(
                Locale.ROOT,
                "%s won %d of %d %s",
                radiant > dire ? "Radiant" : "Dire",
                Math.max(radiant, dire),
                total,
                things);
    return new Verdict(text, Accent.NEUTRAL);
  }

  /** {@code 1 death}, {@code 2 deaths}. */
  static String count(long n, String one) {
    return n + " " + one + (n == 1 ? "" : "s");
  }
}
