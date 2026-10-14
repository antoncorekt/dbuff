package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.springframework.stereotype.Component;

/**
 * The one place that words a match thread's header: its title for each stage, as Components V2 and
 * as the plain text posted when Discord rejects the components, and the headers the channel had
 * before stages existed.
 */
@Component
public class HeaderRenderer {
  private static final String SEPARATOR = " · ";
  private static final String LINK_PREFIX = "-# ";
  private static final int SECONDS_PER_MINUTE = 60;
  private static final DateTimeFormatter START_FORMATTER =
      DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  public ReportMessage.Components render(StageTarget target, MatchFacts facts) {
    String title = title(target, facts);
    String link = LINK_PREFIX + facts.link();
    List<ContainerChildComponent> children =
        List.of(TextDisplay.of("**" + title + "**"), TextDisplay.of(link));
    List<MessageTopLevelComponent> top =
        List.of(Container.of(children).withAccentColor(facts.accent().rgb()));
    int textLength = title.length() + 4 + link.length();
    return new ReportMessage.Components(
        top, textLength, children.size() + 1, title + "\n" + facts.link());
  }

  public String title(StageTarget target, MatchFacts facts) {
    List<String> parts = new ArrayList<>();
    parts.add("Match " + facts.matchId());
    if (facts.startSeconds() != null) {
      parts.add(started(facts));
    }
    if (facts.mode() != null) {
      parts.add(facts.mode());
    }
    if (facts.result().known()) {
      parts.add(facts.result().label() + score(target.stage(), facts));
    }
    if (facts.durationSeconds() != null) {
      parts.add(facts.durationSeconds() / SECONDS_PER_MINUTE + " min");
    }
    parts.add(status(target));
    return String.join(SEPARATOR, parts);
  }

  private static String started(MatchFacts facts) {
    ZoneId zone = facts.zone() == null ? ZoneId.systemDefault() : facts.zone();
    return Instant.ofEpochSecond(facts.startSeconds()).atZone(zone).format(START_FORMATTER);
  }

  private static String status(StageTarget target) {
    boolean partial = target.partial();
    return switch (target.stage()) {
      case REQUESTED -> "OpenDota parse requested";
      case PARSED -> "OpenDota data ready";
      case REPLAY_PENDING -> "replay processing";
      case REPLAY_DONE -> partial ? "replay analysis ready (partial)" : "replay analysis ready";
      case REPLAY_UNAVAILABLE -> "replay analysis not available";
      case PARSE_TIMED_OUT -> "OpenDota did not parse this match";
    };
  }

  private static String score(MatchReportStage stage, MatchFacts facts) {
    if (stage == MatchReportStage.REQUESTED
        || facts.radiantScore() == null
        || facts.direScore() == null) {
      return "";
    }
    return " (" + facts.radiantScore() + "-" + facts.direScore() + ")";
  }

  /** The header a channel without stages gets when a match is first seen. */
  public String legacyWaiting(long matchId) {
    return "Match " + matchId + " — waiting for parsed data...";
  }

  /** The header a channel without stages gets once the match is parsed. */
  public String legacyParsed(MatchDomain match, String winLoss) {
    StringBuilder sb = new StringBuilder();
    sb.append("Match ").append(match.getId());

    if (match.getGameModeName() != null) {
      sb.append(" - ").append(match.getGameModeName());
    }

    sb.append(" - ").append(winLoss);

    if (match.getRadiantScore() != null && match.getDireScore() != null) {
      sb.append(" (")
          .append(match.getRadiantScore())
          .append("-")
          .append(match.getDireScore())
          .append(")");
    }

    if (match.getStartTime() != null) {
      String date =
          Instant.ofEpochSecond(match.getStartTime())
              .atZone(ZoneId.systemDefault())
              .format(DATE_FORMATTER);
      sb.append("; match date - ").append(date);
    } else if (match.getStartLocalDate() != null) {
      sb.append("; match date - ").append(match.getStartLocalDate());
    }

    if (match.getDuration() != null) {
      long minutes = match.getDuration() / SECONDS_PER_MINUTE;
      sb.append(" with duration ").append(minutes).append(" min");
    }

    return sb.toString();
  }
}
