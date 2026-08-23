package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;
import com.ako.dbuff.resources.model.ItemComboStatisticResponse;
import com.ako.dbuff.resources.model.PlayerStatisticResponse;
import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.DbuffCommand;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver;
import com.ako.dbuff.service.discord.command.StatsEmbedFormatter;
import com.ako.dbuff.service.discord.command.StatsOptions;
import com.ako.dbuff.service.discord.command.StatsRequestResolver;
import com.ako.dbuff.service.discord.command.StatsRequestResolver.StatsRequest;
import com.ako.dbuff.service.ranking.AbilityRankingService;
import com.ako.dbuff.service.ranking.ItemRankingService;
import com.ako.dbuff.service.ranking.PlayerStatisticService;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * {@code /stats} — player statistics.
 *
 * <p>All four subcommands share {@link StatsRequestResolver}'s validation sequence, which runs
 * before anything is acknowledged. See that class for why the order matters.
 *
 * <p>{@code items} and {@code skills} each have two modes. With the option absent they answer "what
 * does this player buy / cast most", a ranking grouped by item. With it present they answer "how
 * does this player do when they get all of these in one game", which is a conjunction the ranking
 * query cannot express. Both are useful and they are not the same question.
 *
 * <p>{@code heroes} takes {@code hero:} for the same reason: unfiltered it ranks the heroes played,
 * and filtered it collapses to that hero's own record. Unlike {@code overall}, the table is kept
 * when filtered — there it degenerates to the hero already named in the request, but here the table
 * <em>is</em> the answer.
 *
 * <p>{@code skills} additionally accepts {@code items}, which intersects the two conjunctions:
 * games where the player used all of those skills <em>and</em> held all of those items. That is a
 * third question again — a skill build's win rate can depend entirely on whether the item that
 * enables it was bought.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsCommand implements DbuffCommand {

  private final StatsRequestResolver requestResolver;
  private final PlayerStatisticService playerStatisticService;
  private final ItemRankingService itemRankingService;
  private final AbilityRankingService abilityRankingService;
  private final StatsEmbedFormatter formatter;

  @Override
  public String getName() {
    return "stats";
  }

  @Override
  public SlashCommandData getDefinition() {
    return Commands.slash("stats", "Player statistics")
        .addSubcommands(
            new SubcommandData("overall", "Win/loss, KDA and farm for a player")
                .addOptions(
                    StatsOptions.player(),
                    StatsOptions.hero(),
                    StatsOptions.period(),
                    StatsOptions.gameMode()),
            new SubcommandData("heroes", "Most played heroes, or your record on one of them")
                .addOptions(
                    StatsOptions.player(),
                    StatsOptions.hero(),
                    StatsOptions.period(),
                    StatsOptions.gameMode(),
                    StatsOptions.limit()),
            new SubcommandData("items", "Item statistics, or stats for a set of items in one game")
                .addOptions(
                    StatsOptions.player(),
                    new OptionData(
                        OptionType.STRING,
                        "items",
                        "Items that must all appear in the same game",
                        false,
                        true),
                    StatsOptions.hero(),
                    StatsOptions.period(),
                    StatsOptions.gameMode(),
                    StatsOptions.limit()),
            new SubcommandData(
                    "skills", "Skill statistics, or stats for a set of skills in one game")
                .addOptions(
                    StatsOptions.player(),
                    new OptionData(
                        OptionType.STRING,
                        "skills",
                        "Skills that must all appear in the same game",
                        false,
                        true),
                    new OptionData(
                        OptionType.STRING,
                        "items",
                        "Items that must appear in the same game as those skills",
                        false,
                        true),
                    StatsOptions.hero(),
                    StatsOptions.period(),
                    StatsOptions.gameMode(),
                    StatsOptions.limit()));
  }

  @Override
  public void execute(String subcommand, CommandContext context) {
    switch (subcommand == null ? "overall" : subcommand) {
      case "heroes" -> heroes(context);
      case "items" -> items(context);
      case "skills" -> skills(context);
      default -> overall(context);
    }
  }

  private void overall(CommandContext context) {
    requestResolver
        .prepare(context)
        .ifPresent(
            request -> {
              AsyncReply reply = acknowledge(context, request, "📊", "Overall stats");
              perPlayer(
                  request,
                  reply,
                  player ->
                      formatter.formatOverall(
                          playerStatisticService.getPlayerStatistics(
                              player.accountId(),
                              request.startDate(),
                              request.endDate(),
                              null,
                              request.heroNames(),
                              request.gameModeNames()),
                          request.footer()));
            });
  }

  private void heroes(CommandContext context) {
    requestResolver
        .prepare(context)
        .ifPresent(
            request -> {
              int limit = requestResolver.resolveLimit(context);
              AsyncReply reply = acknowledge(context, request, "🦸", "Most played heroes");
              perPlayer(
                  request,
                  reply,
                  player -> {
                    PlayerStatisticResponse stats =
                        playerStatisticService.getPlayerStatistics(
                            player.accountId(),
                            request.startDate(),
                            request.endDate(),
                            limit,
                            request.heroNames(),
                            request.gameModeNames());
                    return formatter.formatHeroes(stats, request.footer());
                  });
            });
  }

  private void items(CommandContext context) {
    requestResolver
        .prepare(context)
        .ifPresent(
            request -> {
              // After prepare, so an unregistered channel is reported as such rather than as a
              // typo, but still before acknowledge, so a typo never creates a thread.
              Set<String> itemNames = requestResolver.optionAsSet(context, "items");
              if (requestResolver.reportUnknownItems(context, itemNames)) {
                return;
              }

              int limit = requestResolver.resolveLimit(context);
              String title =
                  itemNames.isEmpty() ? "Item stats" : "Item combo (" + join(itemNames) + ")";
              AsyncReply reply = acknowledge(context, request, "🎒", title);

              perPlayer(
                  request,
                  reply,
                  player -> {
                    if (itemNames.isEmpty()) {
                      return formatter.formatItemRanking(
                          player.name(),
                          itemRankingService.getItemRankings(
                              player.accountId(),
                              request.startDate(),
                              request.endDate(),
                              null,
                              null,
                              request.heroNames(),
                              request.gameModeNames(),
                              limit),
                          request.footer());
                    }
                    ItemComboStatisticResponse combo =
                        itemRankingService.getItemComboStatistics(
                            player.accountId(),
                            request.startDate(),
                            request.endDate(),
                            itemNames,
                            request.heroNames(),
                            request.gameModeNames());
                    if (combo.getGamesFound() == null || combo.getGamesFound() == 0L) {
                      reply.post(
                          "🔍 **"
                              + player.name()
                              + "** has no games with all of: "
                              + join(itemNames)
                              + ".");
                      return null;
                    }
                    return formatter.formatItemCombo(combo, request.footer());
                  });
            });
  }

  private void skills(CommandContext context) {
    requestResolver
        .prepare(context)
        .ifPresent(
            request -> {
              Set<String> skillNames = requestResolver.optionAsSet(context, "skills");
              Set<String> itemNames = requestResolver.optionAsSet(context, "items");
              if (requestResolver.reportUnknownSkills(context, skillNames)
                  || requestResolver.reportUnknownItems(context, itemNames)) {
                return;
              }
              // Items alone cannot narrow a skill ranking — the conjunction needs a skill to be a
              // conjunction with. Saying so beats answering the unfiltered top-N question instead.
              if (skillNames.isEmpty() && !itemNames.isEmpty()) {
                context.replyEphemeral(
                    "❌ Name at least one skill to combine those items with, or use `/stats items`.");
                return;
              }

              int limit = requestResolver.resolveLimit(context);
              AsyncReply reply =
                  acknowledge(context, request, "✨", skillsTitle(skillNames, itemNames));

              perPlayer(
                  request,
                  reply,
                  player -> {
                    if (skillNames.isEmpty()) {
                      return formatter.formatAbilityRanking(
                          player.name(),
                          abilityRankingService.getAbilityRankings(
                              player.accountId(),
                              request.startDate(),
                              request.endDate(),
                              null,
                              null,
                              request.heroNames(),
                              request.gameModeNames(),
                              limit),
                          request.footer());
                    }
                    AbilityComboStatisticResponse combo =
                        abilityRankingService.getAbilityComboStatistics(
                            player.accountId(),
                            request.startDate(),
                            request.endDate(),
                            skillNames,
                            itemNames,
                            request.heroNames(),
                            request.gameModeNames());
                    if (combo.getGamesFound() == null || combo.getGamesFound() == 0L) {
                      reply.post(
                          "🔍 **"
                              + player.name()
                              + "** has no games with all of: "
                              + join(skillNames)
                              + (itemNames.isEmpty() ? "" : " + " + join(itemNames))
                              + ".");
                      return null;
                    }
                    return formatter.formatAbilityCombo(combo, request.footer());
                  });
            });
  }

  private String skillsTitle(Set<String> skillNames, Set<String> itemNames) {
    if (skillNames.isEmpty()) {
      return "Skill stats";
    }
    if (itemNames.isEmpty()) {
      return "Skill combo (" + join(skillNames) + ")";
    }
    return "Skill + item combo (" + join(skillNames) + " + " + join(itemNames) + ")";
  }

  /**
   * Acknowledges with a summary that names the question asked.
   *
   * <p>"Fetching statistics for 4 player(s)" was the same message for all four subcommands, so a
   * thread gave no clue which one produced it — and the threads outlive the invocation that made
   * them.
   */
  private AsyncReply acknowledge(
      CommandContext context, StatsRequest request, String emoji, String title) {
    return context.acknowledge(
        emoji
            + " "
            + title
            + " — "
            + request.playerNames()
            + " · "
            + request.footer()
            + request.omissionNotice()
            + "…",
        title + ": " + request.playerNames());
  }

  /**
   * Runs {@code work} for each player, posting each embed as it completes.
   *
   * <p>Posting per player rather than collecting first means a slow five-player request shows its
   * first answer immediately, and one player's failure costs only that player's result — the rest
   * still arrive.
   *
   * @param work returns the embed to post, or null when it has already posted its own message
   */
  private void perPlayer(
      StatsRequest request,
      AsyncReply reply,
      Function<PlayerReferenceResolver.ResolvedPlayer, MessageEmbed> work) {

    for (PlayerReferenceResolver.ResolvedPlayer player : request.players()) {
      try {
        MessageEmbed embed = work.apply(player);
        if (embed != null) {
          reply.postEmbed(embed);
        }
      } catch (RuntimeException e) {
        log.warn("Statistics failed for player {}", player.accountId(), e);
        reply.fail(
            "⚠️ Could not fetch statistics for **" + player.name() + "**: " + e.getMessage());
      }
    }
  }

  private String join(Set<String> names) {
    return String.join(", ", names);
  }
}
