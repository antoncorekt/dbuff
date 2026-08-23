package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.CurrentPatchDateResolver;
import com.ako.dbuff.service.constant.GameModeResolver;
import com.ako.dbuff.service.constant.GameModeSelection;
import com.ako.dbuff.service.constant.NameResolution;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.ranking.StatsPeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The validation sequence every statistics command shares, and the order of it is the contract.
 *
 * <p>Everything that can fail cheaply is checked <em>before</em> the caller reaches {@link
 * CommandContext#acknowledge}, because acknowledging creates a thread and Discord will not let an
 * ephemeral message carry one. A typo therefore gets a private correction, not a thread full of an
 * error.
 *
 * <p>Extracted from {@code StatsCommand} when {@code /hero} arrived. Two copies of this sequence
 * would drift, and the way it drifts is silent: a command that acknowledges before validating still
 * works for every correct input.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsRequestResolver {

  /**
   * Cap on players per invocation. Each one is a separate aggregation over the match history, and a
   * request naming the whole server would sit in the thread for minutes.
   */
  public static final int MAX_PLAYERS = 5;

  /** Discord will not render more than this usefully, and the tables get unreadable first. */
  public static final int MAX_LIMIT = 25;

  public static final int DEFAULT_LIMIT = 10;

  private final DbufInstanceConfigService instanceConfigService;
  private final PlayerReferenceResolver playerResolver;
  private final ConstantNameResolver nameResolver;
  private final GameModeResolver gameModeResolver;
  private final CurrentPatchDateResolver patchDateResolver;

  /**
   * A validated request.
   *
   * @param players the resolved players, in the order asked for
   * @param heroNames the hero filter, empty for none
   * @param gameModeNames the game mode filter, empty for every mode
   * @param startDate inclusive lower bound, null for all time
   * @param endDate inclusive upper bound
   * @param periodLabel period text, which says so when the range fell back
   * @param gameModeLabel mode text, e.g. {@code Ability Draft}
   * @param omittedPlayers how many tracked players the {@value #MAX_PLAYERS} cap left out when the
   *     request defaulted to the whole focus group; zero otherwise
   */
  public record StatsRequest(
      List<PlayerReferenceResolver.ResolvedPlayer> players,
      Set<String> heroNames,
      Set<String> gameModeNames,
      LocalDate startDate,
      LocalDate endDate,
      String periodLabel,
      String gameModeLabel,
      int omittedPlayers) {

    /** Footer text: the period and the mode, which together scope every number in the embed. */
    public String footer() {
      return periodLabel + " · " + gameModeLabel;
    }

    /** The players named, comma-separated, for a summary or thread name. */
    public String playerNames() {
      return String.join(
          ", ", players.stream().map(PlayerReferenceResolver.ResolvedPlayer::name).toList());
    }

    /**
     * Text stating that the group was trimmed, or empty when it was not.
     *
     * <p>Said out loud rather than trimmed quietly: an answer covering five of eight tracked
     * players reads exactly like an answer covering everyone, and the reader has no way to tell.
     */
    public String omissionNotice() {
      if (omittedPlayers <= 0) {
        return "";
      }
      return " (showing "
          + players.size()
          + " of "
          + (players.size() + omittedPlayers)
          + " tracked — name players to pick others)";
    }
  }

  /**
   * Validates everything cheap, replying ephemerally and returning empty on the first problem.
   *
   * @param context the in-flight command
   * @return the validated request, or empty when the user has already been told what was wrong
   */
  public Optional<StatsRequest> prepare(CommandContext context) {
    String channelId = context.getParentChannelId();

    if (instanceConfigService.getByDiscordChannelId(channelId).isEmpty()) {
      context.replyEphemeral(
          "ℹ️ This channel is not tracking any players yet. Use `/dbuff register` first.");
      return Optional.empty();
    }

    List<PlayerReferenceResolver.ResolvedPlayer> players;
    int omittedPlayers = 0;

    List<String> references = context.getOptionAsList("player");
    if (references.isEmpty()) {
      // No player named: answer for the channel's whole focus group, which is the question a
      // shared channel usually means.
      List<PlayerReferenceResolver.ResolvedPlayer> group = playerResolver.focusGroup(channelId);
      if (group.isEmpty()) {
        // Registered but empty, so /dbuff register is the wrong advice here.
        context.replyEphemeral(
            "ℹ️ This channel tracks no players yet. Use `/dbuff add` first, or name a player.");
        return Optional.empty();
      }
      // Trimmed rather than refused: a bare command should still answer something, and the cap is
      // there because each player is another pass over the match history.
      players = group.size() > MAX_PLAYERS ? group.subList(0, MAX_PLAYERS) : group;
      omittedPlayers = group.size() - players.size();
      if (omittedPlayers > 0) {
        log.info(
            "Channel {} tracks {} players; trimming to {} for a request that named none",
            channelId,
            group.size(),
            MAX_PLAYERS);
      }
    } else {
      PlayerReferenceResolver.Resolution resolution = playerResolver.resolve(channelId, references);
      if (resolution.hasUnresolved()) {
        context.replyEphemeral(unresolvedPlayerMessage(context, resolution));
        return Optional.empty();
      }
      if (resolution.isEmpty()) {
        context.replyEphemeral("❌ Name at least one player, or omit the option for everyone.");
        return Optional.empty();
      }
      // Explicitly named players are rejected rather than trimmed: the user chose these, so
      // answering for a subset would drop one of their choices without saying which.
      if (resolution.players().size() > MAX_PLAYERS) {
        context.replyEphemeral(
            "❌ At most "
                + MAX_PLAYERS
                + " players per command; you named "
                + resolution.players().size()
                + ". Each one is a separate pass over the match history.");
        return Optional.empty();
      }
      players = resolution.players();
    }

    Set<String> heroNames = optionAsSet(context, "hero");
    if (reportUnknownHeroes(context, heroNames)) {
      return Optional.empty();
    }

    GameModeSelection modes = gameModeResolver.resolveOrDefault(optionAsSet(context, "game_mode"));
    if (modes.hasUnresolved()) {
      context.replyEphemeral(unknownGameModeMessage(modes));
      return Optional.empty();
    }

    StatsPeriod period = StatsPeriod.fromChoiceValue(context.getOption("period"));
    StatsPeriod.Range range =
        period.resolve(LocalDate.now(), patchDateResolver.getCurrentPatchStartDate().orElse(null));

    return Optional.of(
        new StatsRequest(
            players,
            heroNames,
            modes.canonicalNames(),
            range.startDate(),
            range.endDate(),
            periodLabel(period, range),
            modes.label(),
            omittedPlayers));
  }

  /** Clamps rather than rejects: a user asking for 100 rows wants "as many as you can". */
  public int resolveLimit(CommandContext context) {
    int requested = context.getOptionAsInt("limit", DEFAULT_LIMIT);
    if (requested < 1) {
      return DEFAULT_LIMIT;
    }
    return Math.min(requested, MAX_LIMIT);
  }

  /** A comma-separated option as an ordered set. */
  public Set<String> optionAsSet(CommandContext context, String name) {
    return new LinkedHashSet<>(context.getOptionAsList(name));
  }

  /**
   * Reads a boolean option.
   *
   * <p>{@link CommandContext} has no boolean accessor because nothing needed one before, and the
   * text surface delivers every option as a string regardless — so accept what Discord's picker
   * submits ({@code true}) as well as what someone types by hand.
   *
   * @param context the in-flight command
   * @param name the option name
   * @return true when the option is set to an affirmative value
   */
  public boolean isEnabled(CommandContext context, String name) {
    String raw = context.getOption(name);
    if (raw == null || raw.isBlank()) {
      return false;
    }
    String value = raw.trim();
    return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equals("1");
  }

  /**
   * Reports unknown item names ephemerally, with a "did you mean" for each.
   *
   * @return true when something was unknown and the user has been told
   */
  public boolean reportUnknownItems(CommandContext context, Set<String> names) {
    return reportUnknown(
        context, nameResolver.resolveItems(names), "item", names, nameResolver::suggestItem);
  }

  /** Reports unknown skill names ephemerally. See {@link #reportUnknownItems}. */
  public boolean reportUnknownSkills(CommandContext context, Set<String> names) {
    return reportUnknown(
        context,
        nameResolver.resolveAbilities(names),
        "skill",
        names,
        nameResolver::suggestAbility);
  }

  /** Reports unknown hero names ephemerally. See {@link #reportUnknownItems}. */
  public boolean reportUnknownHeroes(CommandContext context, Set<String> names) {
    return reportUnknown(
        context, nameResolver.resolveHeroes(names), "hero", names, nameResolver::suggestHero);
  }

  private boolean reportUnknown(
      CommandContext context,
      NameResolution resolution,
      String kind,
      Set<String> requested,
      Function<String, Optional<String>> suggest) {

    if (!resolution.hasUnresolved()) {
      return false;
    }
    List<String> parts = new ArrayList<>();
    for (String unknown : resolution.unresolvedNames()) {
      parts.add(
          "`"
              + unknown
              + "`"
              + suggest.apply(unknown).map(s -> " (did you mean `" + s + "`?)").orElse(""));
    }
    context.replyEphemeral("❌ Unknown " + kind + ": " + String.join(", ", parts));
    log.debug("Unknown {} names: {} of {}", kind, resolution.unresolvedNames(), requested);
    return true;
  }

  private String unknownGameModeMessage(GameModeSelection modes) {
    List<String> parts = new ArrayList<>();
    for (String unknown : modes.unresolvedNames()) {
      parts.add(
          "`"
              + unknown
              + "`"
              + gameModeResolver
                  .suggest(unknown)
                  .map(s -> " (did you mean `" + s + "`?)")
                  .orElse(""));
    }
    return "❌ Unknown game mode: "
        + String.join(", ", parts)
        + ". Try `"
        + GameModeResolver.ABILITY_DRAFT
        + "`, `"
        + GameModeResolver.ALL_PICK
        + "` or `"
        + GameModeResolver.ALL
        + "`.";
  }

  private String unresolvedPlayerMessage(
      CommandContext context, PlayerReferenceResolver.Resolution resolution) {
    List<String> parts =
        resolution.unresolved().stream()
            .map(
                unknown ->
                    "`"
                        + unknown
                        + "`"
                        + playerResolver
                            .suggest(context.getParentChannelId(), unknown)
                            .map(suggestion -> " (did you mean `" + suggestion + "`?)")
                            .orElse(""))
            .toList();
    return "❌ Could not find: " + String.join(", ", parts);
  }

  /**
   * Footer text for the period.
   *
   * <p>Says so when {@code CURRENT_PATCH} could not find a patch date and degraded to 30 days.
   * Labelling that result "Current patch" would present an answer to a question the user did not
   * ask, and they would have no way to tell.
   */
  private String periodLabel(StatsPeriod period, StatsPeriod.Range range) {
    if (!range.fellBack()) {
      return period.getDisplayName();
    }
    return period.getDisplayName()
        + " unavailable — showing "
        + StatsPeriod.LAST_30_DAYS.getDisplayName().toLowerCase();
  }
}
