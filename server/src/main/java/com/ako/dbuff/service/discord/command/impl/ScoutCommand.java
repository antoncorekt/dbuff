package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.service.discord.DiscordStatisticFormatter;
import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.DbuffCommand;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.ranking.ExternalPlayerStatisticService;
import com.ako.dbuff.service.ranking.ScoreboardStatisticService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * {@code /scout} — how the channel's focus group has fared against other players.
 *
 * <p>Replaces the {@code !vs} listener. The legacy alias is kept, and its whole argument string
 * maps onto the {@code name} option, so {@code !vs Termit} and {@code /scout player name:Termit}
 * take the same path.
 *
 * <p>The scoreboard mode is now explicit. It used to fire on <em>any</em> image posted in a
 * registered channel, so every meme cost an OpenAI Vision call; asking for it costs nothing until
 * someone actually wants it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoutCommand implements DbuffCommand {

  private final DbufInstanceConfigService instanceConfigService;
  private final ExternalPlayerStatisticService externalPlayerStatisticService;
  private final ScoreboardStatisticService scoreboardStatisticService;
  private final DiscordStatisticFormatter formatter;

  @Override
  public String getName() {
    return "scout";
  }

  @Override
  public List<String> getTextAliases() {
    return List.of("vs");
  }

  @Override
  public SlashCommandData getDefinition() {
    return Commands.slash("scout", "Statistics for players you have played against")
        .addSubcommands(
            new SubcommandData("player", "Scout one opponent by name")
                .addOptions(
                    new OptionData(
                        OptionType.STRING,
                        "name",
                        "Opponent name; case-insensitive, regex supported",
                        true,
                        true)),
            new SubcommandData("scoreboard", "Scout every opponent on a scoreboard screenshot")
                .addOptions(
                    new OptionData(OptionType.ATTACHMENT, "image", "Scoreboard screenshot", true)));
  }

  /** {@code !vs Termit} carries no subcommand, so the whole argument string is the name. */
  @Override
  public Map<String, String> parseTextArguments(String alias, String subcommand, String arguments) {
    String name = joinNonBlank(subcommand, arguments);
    return name.isBlank() ? Map.of() : Map.of("name", name);
  }

  /** The legacy form has no subcommand word — its first token is part of the player name. */
  @Override
  public String resolveTextSubcommand(String alias, String parsedSubcommand) {
    return "player";
  }

  @Override
  public void execute(String subcommand, CommandContext context) {
    Optional<String> instanceId = instanceId(context);
    if (instanceId.isEmpty()) {
      context.replyEphemeral(
          "❌ No instance registered for this channel. Use `/dbuff register` first.");
      return;
    }

    if ("scoreboard".equals(subcommand)) {
      scoreboard(context, instanceId.get());
    } else {
      player(context, instanceId.get());
    }
  }

  private void player(CommandContext context, String instanceId) {
    String name = context.getOption("name");
    if (name == null || name.isBlank()) {
      context.replyEphemeral("❌ Give an opponent name, e.g. `/scout player name:Termit`.");
      return;
    }

    AsyncReply reply = context.acknowledge("🔍 Scouting **" + name + "**…", "vs " + name);
    List<ExternalPlayerStatisticResponse> matches =
        externalPlayerStatisticService.getStatisticsByNamePatternForInstance(instanceId, name);

    if (matches.isEmpty()) {
      reply.post("No players matched `" + name + "`.");
      return;
    }
    if (matches.size() > 1) {
      reply.post("Matched **" + matches.size() + "** players:");
    }
    matches.forEach(match -> reply.postLines(formatter.formatPlayer(match)));
  }

  private void scoreboard(CommandContext context, String instanceId) {
    AsyncReply reply = context.acknowledge("🔍 Reading scoreboard…", "scout: scoreboard");

    // Downloaded after acknowledging: it is network I/O and would otherwise eat the
    // three-second interaction budget.
    Optional<byte[]> image = context.downloadAttachment("image");
    if (image.isEmpty()) {
      reply.fail("❌ No image attached.");
      return;
    }

    List<ExternalPlayerStatisticResponse> opponents =
        scoreboardStatisticService.getStatisticsForInstance(instanceId, image.get());
    if (opponents.isEmpty()) {
      reply.post("No opponents detected on the scoreboard.");
      return;
    }

    reply.post("Found **" + opponents.size() + "** opponents:");
    opponents.forEach(opponent -> reply.postLines(formatter.formatPlayer(opponent)));
  }

  private Optional<String> instanceId(CommandContext context) {
    return instanceConfigService
        .getByDiscordChannelId(context.getParentChannelId())
        .map(DbufInstanceConfigResponse::getId);
  }

  private String joinNonBlank(String first, String second) {
    StringBuilder joined = new StringBuilder();
    if (first != null && !first.isBlank()) {
      joined.append(first.trim());
    }
    if (second != null && !second.isBlank()) {
      if (joined.length() > 0) {
        joined.append(' ');
      }
      joined.append(second.trim());
    }
    return joined.toString();
  }
}
