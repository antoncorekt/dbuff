package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.service.constant.GameModeResolver;
import com.ako.dbuff.service.ranking.StatsPeriod;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * The option definitions the statistics commands share.
 *
 * <p>Here rather than duplicated per command because {@link StatsRequestResolver} reads these
 * options by name: a command that spelled {@code game_mode} differently would compile, register,
 * and then silently ignore whatever the user selected.
 */
public final class StatsOptions {

  private StatsOptions() {}

  /**
   * The player filter. Optional: an omitted {@code player:} means every player the channel tracks,
   * which is the question most often asked of a shared channel.
   */
  public static OptionData player() {
    return new OptionData(
        OptionType.STRING,
        "player",
        "Player, @mention or list; defaults to everyone tracked here",
        false,
        true);
  }

  public static OptionData hero() {
    return new OptionData(OptionType.STRING, "hero", "Restrict to one hero", false, true);
  }

  public static OptionData limit() {
    return new OptionData(OptionType.INTEGER, "limit", "How many rows (max 25)", false);
  }

  /** Static choices, so no autocomplete round trip is needed for a handful of fixed values. */
  public static OptionData period() {
    OptionData option = new OptionData(OptionType.STRING, "period", "Time range", false);
    for (StatsPeriod period : StatsPeriod.values()) {
      option.addChoice(period.getDisplayName(), period.getChoiceValue());
    }
    return option;
  }

  /**
   * The game mode filter. Autocomplete rather than static choices because it is list-valued, and
   * Discord's choice options submit exactly one value.
   */
  public static OptionData gameMode() {
    return new OptionData(
        OptionType.STRING,
        "game_mode",
        "Game mode, or a list; defaults to " + GameModeResolver.ABILITY_DRAFT,
        false,
        true);
  }

  /**
   * Asks for the match IDs behind the numbers, posted as a separate message per player.
   *
   * <p>Off by default: it is another query per player, and most of the time the aggregate is the
   * answer. When a figure looks wrong, though, the only way to check it is to see which games it
   * came from.
   */
  public static OptionData traceMatches() {
    return new OptionData(
        OptionType.BOOLEAN,
        "trace_matches",
        "Also list the match IDs and dates behind the numbers");
  }
}
