package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.service.match.DotaApiLastMatchesService.PlayerLine;
import com.ako.dbuff.service.replay.report.view.Accent;
import java.time.ZoneId;
import java.util.Collection;

/**
 * What a match header says about the match; any part may be null when it is not known yet.
 *
 * @param mode readable game mode, null when unknown
 * @param startSeconds match start, epoch seconds, null when unknown
 * @param zone the zone the start is shown in, null for the server's
 */
public record MatchFacts(
    long matchId,
    String mode,
    Result result,
    Long durationSeconds,
    Long radiantScore,
    Long direScore,
    Long startSeconds,
    ZoneId zone) {

  private static final String MODE_PREFIX = "game_mode_";

  /** How many tracked players won and lost. */
  public record Result(int wins, int losses) {

    public static Result ofStats(Collection<PlayerMatchStatisticDomain> focusStats) {
      int wins =
          (int) focusStats.stream().filter(s -> s.getWin() != null && s.getWin() == 1).count();
      return new Result(wins, focusStats.size() - wins);
    }

    /** Lines whose outcome is unknown count for neither side. */
    public static Result ofLines(Collection<PlayerLine> lines) {
      int wins = (int) lines.stream().filter(l -> Boolean.TRUE.equals(l.won())).count();
      int losses = (int) lines.stream().filter(l -> Boolean.FALSE.equals(l.won())).count();
      return new Result(wins, losses);
    }

    public boolean known() {
      return wins + losses > 0;
    }

    public String label() {
      if (!known()) {
        return "Unknown";
      }
      if (losses == 0) {
        return "Win";
      }
      return wins == 0 ? "Loss" : "Mixed (" + wins + "W/" + losses + "L)";
    }

    public Accent accent() {
      if (!known()) {
        return Accent.INFO;
      }
      if (losses == 0) {
        return Accent.WIN;
      }
      return wins == 0 ? Accent.LOSS : Accent.NEUTRAL;
    }
  }

  /** {@code game_mode_ability_draft} as {@code ability draft}; null for a null or blank name. */
  public static String readableMode(String constantName) {
    if (constantName == null || constantName.isBlank()) {
      return null;
    }
    String name =
        constantName.startsWith(MODE_PREFIX)
            ? constantName.substring(MODE_PREFIX.length())
            : constantName;
    return name.replace('_', ' ');
  }

  /** The facts kept on the row, with the scores of the match when it is known. */
  public static MatchFacts fromRow(MatchReportThreadDomain row, MatchDomain match, ZoneId zone) {
    Result result =
        row.getWins() == null || row.getLosses() == null
            ? new Result(0, 0)
            : new Result(row.getWins(), row.getLosses());
    return new MatchFacts(
        row.getMatchId(),
        row.getModeName(),
        result,
        row.getDurationSeconds(),
        match == null ? null : match.getRadiantScore(),
        match == null ? null : match.getDireScore(),
        match == null ? null : match.getStartTime(),
        zone);
  }

  /** These facts, with what the row kept filling in whatever is still unknown. */
  public MatchFacts orFrom(MatchReportThreadDomain row) {
    MatchFacts kept = fromRow(row, null, zone);
    return new MatchFacts(
        matchId,
        mode != null ? mode : kept.mode(),
        result.known() ? result : kept.result(),
        durationSeconds != null ? durationSeconds : kept.durationSeconds(),
        radiantScore,
        direScore,
        startSeconds,
        zone);
  }

  public Accent accent() {
    return result.accent();
  }

  public String link() {
    return "https://www.dotabuff.com/matches/" + matchId;
  }
}
