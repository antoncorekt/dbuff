package com.ako.dbuff.service.ask;

import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a tool needs to know about the channel it answers for.
 *
 * @param instanceId the dbuff instance of the channel
 * @param channelId the parent text channel
 * @param focusGroup the tracked players of the channel
 * @param asker the player behind "me" and "my"; null when the asker is not linked to a player
 * @param today the date the question was asked
 * @param trackedGameModes the game modes tracked by the channel
 */
public record AskContext(
    String instanceId,
    String channelId,
    List<ResolvedPlayer> focusGroup,
    ResolvedPlayer asker,
    LocalDate today,
    Set<String> trackedGameModes) {

  /** The block that opens the first user turn of a thread. */
  public String toPromptBlock() {
    String players =
        focusGroup.stream()
            .map(p -> p.name() + " (" + p.accountId() + ")")
            .collect(Collectors.joining(", "));
    String askerLine = asker == null ? "unknown" : asker.name() + " (" + asker.accountId() + ")";
    return "<context>\nToday: "
        + today
        + "\nTracked game modes: "
        + String.join(", ", trackedGameModes)
        + "\nFocus group: "
        + players
        + "\nAsker (\"me\", \"my\"): "
        + askerLine
        + "\n</context>\n\n";
  }
}
