package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.resources.model.AbilityRankingResponse;
import com.ako.dbuff.resources.model.ItemRankingResponse;
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
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * {@code /hero} — one hero's report for one or more players.
 *
 * <p>Answers "how do I do on this hero", which {@code /stats overall hero:X} can only half answer:
 * the interesting follow-ups are always what was built and what was skilled on that hero, and
 * asking them meant three separate commands with the hero filter retyped into each.
 *
 * <p>{@code items} and {@code skills} are optional because they cost a query each. Off, this is one
 * aggregation per player; with both on it is three, which is why they are opt-in rather than always
 * included.
 *
 * <p>No subcommands: there is one question here, and the options vary the depth of the answer
 * rather than its subject.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeroCommand implements DbuffCommand {

  private final StatsRequestResolver requestResolver;
  private final PlayerStatisticService playerStatisticService;
  private final ItemRankingService itemRankingService;
  private final AbilityRankingService abilityRankingService;
  private final StatsEmbedFormatter formatter;

  @Override
  public String getName() {
    return "hero";
  }

  @Override
  public SlashCommandData getDefinition() {
    return Commands.slash("hero", "Statistics for one hero, optionally with items and skills")
        .addOptions(
            new OptionData(OptionType.STRING, "hero", "The hero to report on", true, true),
            StatsOptions.player(),
            new OptionData(OptionType.BOOLEAN, "items", "Also list the top items on this hero"),
            new OptionData(OptionType.BOOLEAN, "skills", "Also list the top skills on this hero"),
            StatsOptions.period(),
            StatsOptions.gameMode(),
            StatsOptions.limit());
  }

  @Override
  public void execute(String subcommand, CommandContext context) {
    // The hero option is required by the slash definition, but the text surface has no such
    // guarantee — and an absent hero would silently widen this into /stats overall.
    String heroName = context.getOption("hero");
    if (heroName == null || heroName.isBlank()) {
      context.replyEphemeral("❌ Name a hero, e.g. `/hero hero:Invoker player:Tigress`.");
      return;
    }

    requestResolver
        .prepare(context)
        .ifPresent(
            request -> {
              boolean withItems = isEnabled(context, "items");
              boolean withSkills = isEnabled(context, "skills");
              int limit = requestResolver.resolveLimit(context);
              // From the request, not the raw option: prepare() has validated these against the
              // hero dictionary, so the title cannot name a hero the queries did not use.
              String heroes = String.join(", ", request.heroNames());

              AsyncReply reply = acknowledge(context, request, heroes);
              for (PlayerReferenceResolver.ResolvedPlayer player : request.players()) {
                try {
                  reply.postEmbed(report(request, player, heroes, withItems, withSkills, limit));
                } catch (RuntimeException e) {
                  log.warn("Hero statistics failed for player {}", player.accountId(), e);
                  reply.fail(
                      "⚠️ Could not fetch "
                          + heroes
                          + " statistics for **"
                          + player.name()
                          + "**: "
                          + e.getMessage());
                }
              }
            });
  }

  /**
   * One player's report on the hero.
   *
   * <p>The hero filter comes from {@code request.heroNames()} rather than a second copy: {@code
   * /hero} has no separate {@code hero:} filter to combine, because the hero <em>is</em> the
   * subject.
   */
  private MessageEmbed report(
      StatsRequest request,
      PlayerReferenceResolver.ResolvedPlayer player,
      String heroDisplayName,
      boolean withItems,
      boolean withSkills,
      int limit) {

    Set<String> heroes = request.heroNames();

    PlayerStatisticResponse stats =
        playerStatisticService.getPlayerStatistics(
            player.accountId(),
            request.startDate(),
            request.endDate(),
            null,
            heroes,
            request.gameModeNames());

    List<ItemRankingResponse> items =
        withItems
            ? itemRankingService.getItemRankings(
                player.accountId(),
                request.startDate(),
                request.endDate(),
                null,
                null,
                heroes,
                request.gameModeNames(),
                limit)
            : null;

    List<AbilityRankingResponse> skills =
        withSkills
            ? abilityRankingService.getAbilityRankings(
                player.accountId(),
                request.startDate(),
                request.endDate(),
                null,
                null,
                heroes,
                request.gameModeNames(),
                limit)
            : null;

    return formatter.formatHero(heroDisplayName, stats, items, skills, request.footer());
  }

  private AsyncReply acknowledge(
      CommandContext context, StatsRequest request, String heroDisplayName) {
    String title = heroDisplayName + " stats";
    return context.acknowledge(
        "🦸 " + title + " — " + request.playerNames() + " · " + request.footer() + "…",
        title + ": " + request.playerNames());
  }

  /**
   * Reads a boolean option.
   *
   * <p>{@link CommandContext} has no boolean accessor because nothing needed one before, and the
   * text surface delivers every option as a string regardless — so accept what Discord's picker
   * submits ({@code true}) as well as what someone types by hand.
   */
  private boolean isEnabled(CommandContext context, String name) {
    String raw = context.getOption(name);
    if (raw == null || raw.isBlank()) {
      return false;
    }
    String value = raw.trim();
    return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equals("1");
  }
}
