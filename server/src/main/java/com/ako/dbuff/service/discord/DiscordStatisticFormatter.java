package com.ako.dbuff.service.discord;

import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse.HistoryEntry;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse.WinLoseStat;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Formats {@link ExternalPlayerStatisticResponse} data into Discord-ready messages.
 *
 * <p>Each returned string is kept under {@link #MAX_MESSAGE_LENGTH} characters so it can be sent as
 * a single Discord message (the hard limit is 2000). Long histories are split across several
 * messages and capped at {@link #MAX_HISTORY_ENTRIES} entries.
 */
@Service
public class DiscordStatisticFormatter {

  /** Kept safely below Discord's 2000-character hard limit. */
  static final int MAX_MESSAGE_LENGTH = 1900;

  /** Most recent matches to list per player, to avoid flooding the thread. */
  static final int MAX_HISTORY_ENTRIES = 25;

  /**
   * Formats a single external player's statistics into one or more Discord messages.
   *
   * @param response the statistics to format
   * @return an ordered list of message chunks (never empty)
   */
  public List<String> formatPlayer(ExternalPlayerStatisticResponse response) {
    String header = buildHeader(response);

    // Player not found / no linked account: header only.
    if (response.getPlayerId() == null) {
      return List.of(header);
    }

    List<HistoryEntry> history = response.getHistory() == null ? List.of() : response.getHistory();

    List<String> lines = new ArrayList<>();
    int shown = Math.min(history.size(), MAX_HISTORY_ENTRIES);
    for (int i = 0; i < shown; i++) {
      lines.add(historyLine(history.get(i)));
    }
    if (history.size() > MAX_HISTORY_ENTRIES) {
      lines.add("… and " + (history.size() - MAX_HISTORY_ENTRIES) + " more matches");
    }

    return chunk(header, lines);
  }

  private String buildHeader(ExternalPlayerStatisticResponse response) {
    StringBuilder header = new StringBuilder();
    header.append("**").append(safe(response.getPlayerName())).append("**");
    if (response.getPlayerId() != null) {
      header.append(" (id: ").append(response.getPlayerId()).append(")");
    }
    header.append("\n");

    if (response.getPlayerId() == null) {
      header.append("_No data — player not found._");
      return header.toString();
    }

    WinLoseStat against = response.getAgainstStat();
    WinLoseStat teammate = response.getTeammateStat();
    header.append(
        String.format(
            "🆚 Against: **%d**W / **%d**L 🤝 With: **%d**W / **%d**L",
            against.getWin(), against.getLose(), teammate.getWin(), teammate.getLose()));
    return header.toString();
  }

  private String historyLine(HistoryEntry entry) {
    String role = entry.isTeammate() ? "🤝" : "🆚";
    String result = entry.isPlayerWon() ? "✅W" : "❌L";
    String date = entry.getMatchDate() != null ? entry.getMatchDate().toString() : "—";
    String hero =
        entry.getMatchStats() != null && entry.getMatchStats().getPlayerHero() != null
            ? entry.getMatchStats().getPlayerHero()
            : "?";
    // Wrap the link in <> so Discord does not render a preview embed for every match.
    String link = entry.getDotabuffLink() != null ? " <" + entry.getDotabuffLink() + ">" : "";
    return String.format(
        "`%d` %s %s %s — %s%s", entry.getMatchId(), date, role, result, hero, link);
  }

  /** Splits the header plus history lines into messages under {@link #MAX_MESSAGE_LENGTH}. */
  private List<String> chunk(String header, List<String> lines) {
    List<String> messages = new ArrayList<>();
    StringBuilder current = new StringBuilder(header);

    for (String line : lines) {
      if (current.length() + 1 + line.length() > MAX_MESSAGE_LENGTH) {
        messages.add(current.toString());
        current = new StringBuilder();
      }
      if (current.length() > 0) {
        current.append("\n");
      }
      current.append(line);
    }
    messages.add(current.toString());
    return messages;
  }

  private String safe(String value) {
    return value == null ? "Unknown" : value;
  }
}
