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

  public static OptionData player() {
    return new OptionData(
        OptionType.STRING, "player", "Player, @mention or comma-separated list", true, true);
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
}
