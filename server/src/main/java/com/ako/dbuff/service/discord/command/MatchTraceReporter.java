package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.resources.model.MatchReference;
import com.ako.dbuff.service.discord.command.StatsRequestResolver.StatsRequest;
import com.ako.dbuff.service.ranking.PlayerStatisticService;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Posts the match IDs behind a statistics answer, when {@code trace_matches:} was asked for.
 *
 * <p>A separate plain message rather than a field on the embed: an embed field caps at 1024
 * characters and this list is long, and the IDs are meant to be copied out — Discord lets a reader
 * select text from a message far more easily than from an embed.
 *
 * <p>Posted per player, because a five-player request produces five different sets of games and one
 * merged list could not be attributed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchTraceReporter {

  /**
   * Cap on matches listed per player: the last ten games.
   *
   * <p>Ten because the point is to spot-check a figure against recent games, not to dump a season —
   * an all-time trace would run to hundreds, and {@link AsyncReply#post} would split that across a
   * dozen messages and drown the statistics it is meant to explain. What is dropped is stated
   * rather than elided, so a partial list never reads as a complete one.
   */
  static final int MAX_TRACED_MATCHES = 10;

  private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

  /** Shown for a match whose date the scraper never recorded. */
  static final String UNKNOWN_DATE = "date unknown";

  private final PlayerStatisticService playerStatisticService;

  /**
   * Fetches and posts one player's match list.
   *
   * <p>Failures are logged and reported into the thread but never rethrown: the trace is an extra
   * on top of an answer that has already been posted, so losing it must not lose the statistics
   * too.
   *
   * @param reply the thread to post into
   * @param request the validated request, supplying the same filters the numbers used
   * @param player the player whose matches to list
   * @param restrictToMatchIds the combo queries' own match set, or null to trace the whole
   *     selection
   */
  public void post(
      AsyncReply reply,
      StatsRequest request,
      PlayerReferenceResolver.ResolvedPlayer player,
      Set<Long> restrictToMatchIds) {

    try {
      List<MatchReference> matches =
          playerStatisticService.getPlayerMatches(
              player.accountId(),
              request.startDate(),
              request.endDate(),
              request.heroNames(),
              request.gameModeNames(),
              restrictToMatchIds,
              MAX_TRACED_MATCHES + 1);

      reply.post(format(player.name(), matches));
    } catch (RuntimeException e) {
      log.warn("Match trace failed for player {}", player.accountId(), e);
      reply.post("⚠️ Could not list matches for **" + player.name() + "**: " + e.getMessage());
    }
  }

  /**
   * Renders the list.
   *
   * <p>Fenced as a code block so Discord neither linkifies the IDs nor reflows the columns, and so
   * the whole block can be copied in one go.
   *
   * @param playerName the player the matches belong to
   * @param matches the matches, one more than the cap when there are more to report
   * @return the message to post
   */
  String format(String playerName, List<MatchReference> matches) {
    if (matches.isEmpty()) {
      return "🧾 No matches for **" + playerName + "** in this selection.";
    }

    boolean truncated = matches.size() > MAX_TRACED_MATCHES;
    List<MatchReference> shown = truncated ? matches.subList(0, MAX_TRACED_MATCHES) : matches;

    StringBuilder message = new StringBuilder();
    message
        .append("🧾 Matches for **")
        .append(playerName)
        .append("** (")
        .append(shown.size())
        .append(truncated ? " most recent of more" : "")
        .append(")\n```\n");

    for (MatchReference match : shown) {
      message
          .append(match.matchId())
          .append(" - ")
          .append(match.startDate() == null ? UNKNOWN_DATE : DATE.format(match.startDate()))
          .append('\n');
    }
    message.append("```");

    if (truncated) {
      message
          .append("\nLast ")
          .append(MAX_TRACED_MATCHES)
          .append(" only — narrow the period to see earlier games.");
    }
    return message.toString();
  }
}
