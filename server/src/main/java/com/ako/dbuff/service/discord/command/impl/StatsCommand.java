package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;
import com.ako.dbuff.resources.model.ItemComboStatisticResponse;
import com.ako.dbuff.resources.model.PlayerStatisticResponse;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.CurrentPatchDateResolver;
import com.ako.dbuff.service.constant.NameResolution;
import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.DbuffCommand;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver;
import com.ako.dbuff.service.discord.command.StatsEmbedFormatter;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.ranking.AbilityRankingService;
import com.ako.dbuff.service.ranking.ItemRankingService;
import com.ako.dbuff.service.ranking.PlayerStatisticService;
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
 * <p>All four subcommands share one validation sequence, and the order of it is the contract:
 * everything that can fail cheaply is checked <em>before</em> {@link CommandContext#acknowledge},
 * because acknowledging creates a thread and Discord will not let an ephemeral message carry one. A
 * typo therefore gets a private correction, not a thread full of an error.
 *
 * <p>{@code items} and {@code skills} each have two modes. With the option absent they answer "what
 * does this player buy / cast most", a ranking grouped by item. With it present they answer "how
 * does this player do when they get all of these in one game", which is a conjunction the ranking
 * query cannot express. Both are useful and they are not the same question.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsCommand implements DbuffCommand {

  /**
   * Cap on players per invocation. Each one is a separate aggregation over the match history, and a
   * request naming the whole server would sit in the thread for minutes.
   */
  static final int MAX_PLAYERS = 5;

  /** Discord will not render more than this usefully, and the tables get unreadable first. */
  static final int MAX_LIMIT = 25;

  static final int DEFAULT_LIMIT = 10;

  private final DbufInstanceConfigService instanceConfigService;
  private final PlayerReferenceResolver playerResolver;
  private final ConstantNameResolver nameResolver;
  private final CurrentPatchDateResolver patchDateResolver;
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
                .addOptions(playerOption(), heroOption(), periodOption()),
            new SubcommandData("heroes", "Most played heroes for a player")
                .addOptions(playerOption(), periodOption(), limitOption()),
            new SubcommandData("items", "Item statistics, or stats for a set of items in one game")
                .addOptions(
                    playerOption(),
                    new OptionData(
                        OptionType.STRING,
                        "items",
                        "Items that must all appear in the same game",
                        false,
                        true),
                    heroOption(),
                    periodOption(),
                    limitOption()),
            new SubcommandData(
                    "skills", "Skill statistics, or stats for a set of skills in one game")
                .addOptions(
                    playerOption(),
                    new OptionData(
                        OptionType.STRING,
                        "skills",
                        "Skills that must all appear in the same game",
                        false,
                        true),
                    heroOption(),
                    periodOption(),
                    limitOption()));
  }

  private OptionData playerOption() {
    return new OptionData(
        OptionType.STRING, "player", "Player, @mention or comma-separated list", true, true);
  }

  private OptionData heroOption() {
    return new OptionData(OptionType.STRING, "hero", "Restrict to one hero", false, true);
  }

  private OptionData limitOption() {
    return new OptionData(OptionType.INTEGER, "limit", "How many rows (max 25)", false);
  }

  /** Static choices, so no autocomplete round trip is needed for four fixed values. */
  private OptionData periodOption() {
    OptionData option = new OptionData(OptionType.STRING, "period", "Time range", false);
    for (StatsPeriod period : StatsPeriod.values()) {
      option.addChoice(period.getDisplayName(), period.getChoiceValue());
    }
    return option;
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
    prepare(context)
        .ifPresent(
            request -> {
              AsyncReply reply = acknowledge(context, request, "stats");
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
                              request.heroNames()),
                          request.label()));
            });
  }

  private void heroes(CommandContext context) {
    prepare(context)
        .ifPresent(
            request -> {
              int limit = resolveLimit(context);
              AsyncReply reply = acknowledge(context, request, "stats heroes");
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
                            request.heroNames());
                    return formatter.formatHeroes(stats, request.label());
                  });
            });
  }

  private void items(CommandContext context) {
    prepare(context)
        .ifPresent(
            request -> {
              // After prepare, so an unregistered channel is reported as such rather than as a
              // typo, but still before acknowledge, so a typo never creates a thread.
              Set<String> itemNames = optionAsSet(context, "items");
              if (reportUnknown(
                  context, nameResolver.resolveItems(itemNames), "item", itemNames, true)) {
                return;
              }

              int limit = resolveLimit(context);
              AsyncReply reply = acknowledge(context, request, "stats items");
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
                              limit),
                          request.label());
                    }
                    ItemComboStatisticResponse combo =
                        itemRankingService.getItemComboStatistics(
                            player.accountId(),
                            request.startDate(),
                            request.endDate(),
                            itemNames,
                            request.heroNames());
                    if (combo.getGamesFound() == null || combo.getGamesFound() == 0L) {
                      reply.post(
                          "🔍 **"
                              + player.name()
                              + "** has no games with all of: "
                              + String.join(", ", itemNames)
                              + ".");
                      return null;
                    }
                    return formatter.formatItemCombo(combo, request.label());
                  });
            });
  }

  private void skills(CommandContext context) {
    prepare(context)
        .ifPresent(
            request -> {
              Set<String> skillNames = optionAsSet(context, "skills");
              if (reportUnknown(
                  context, nameResolver.resolveAbilities(skillNames), "skill", skillNames, false)) {
                return;
              }

              int limit = resolveLimit(context);
              AsyncReply reply = acknowledge(context, request, "stats skills");
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
                              limit),
                          request.label());
                    }
                    AbilityComboStatisticResponse combo =
                        abilityRankingService.getAbilityComboStatistics(
                            player.accountId(),
                            request.startDate(),
                            request.endDate(),
                            skillNames,
                            request.heroNames());
                    if (combo.getGamesFound() == null || combo.getGamesFound() == 0L) {
                      reply.post(
                          "🔍 **"
                              + player.name()
                              + "** has no games with all of: "
                              + String.join(", ", skillNames)
                              + ".");
                      return null;
                    }
                    return formatter.formatAbilityCombo(combo, request.label());
                  });
            });
  }

  /**
   * A validated request. Exists so the four subcommands share one validation sequence instead of
   * four drifting copies.
   *
   * @param players the resolved players, in the order asked for
   * @param heroNames the hero filter, empty for none
   * @param startDate inclusive lower bound, null for all time
   * @param endDate inclusive upper bound
   * @param label period text for the embed footer, which says so when the range fell back
   */
  private record Request(
      List<PlayerReferenceResolver.ResolvedPlayer> players,
      Set<String> heroNames,
      LocalDate startDate,
      LocalDate endDate,
      String label) {}

  /**
   * Validates everything cheap, replying ephemerally and returning empty on the first problem.
   *
   * @return the validated request, or empty when the user has already been told what was wrong
   */
  private Optional<Request> prepare(CommandContext context) {
    String channelId = context.getParentChannelId();

    if (instanceConfigService.getByDiscordChannelId(channelId).isEmpty()) {
      context.replyEphemeral(
          "ℹ️ This channel is not tracking any players yet. Use `/dbuff register` first.");
      return Optional.empty();
    }

    List<String> references = context.getOptionAsList("player");
    if (references.isEmpty()) {
      context.replyEphemeral("❌ Name at least one player.");
      return Optional.empty();
    }

    PlayerReferenceResolver.Resolution resolution = playerResolver.resolve(channelId, references);
    if (resolution.hasUnresolved()) {
      context.replyEphemeral(unresolvedPlayerMessage(context, resolution));
      return Optional.empty();
    }
    if (resolution.isEmpty()) {
      context.replyEphemeral("❌ Name at least one player.");
      return Optional.empty();
    }
    if (resolution.players().size() > MAX_PLAYERS) {
      context.replyEphemeral(
          "❌ At most "
              + MAX_PLAYERS
              + " players per command; you named "
              + resolution.players().size()
              + ". Each one is a separate pass over the match history.");
      return Optional.empty();
    }

    Set<String> heroNames = optionAsSet(context, "hero");
    if (reportUnknown(context, nameResolver.resolveHeroes(heroNames), "hero", heroNames, null)) {
      return Optional.empty();
    }

    StatsPeriod period = StatsPeriod.fromChoiceValue(context.getOption("period"));
    StatsPeriod.Range range =
        period.resolve(LocalDate.now(), patchDateResolver.getCurrentPatchStartDate().orElse(null));

    return Optional.of(
        new Request(
            resolution.players(),
            heroNames,
            range.startDate(),
            range.endDate(),
            label(period, range)));
  }

  private AsyncReply acknowledge(CommandContext context, Request request, String threadPrefix) {
    String names =
        String.join(
            ", ",
            request.players().stream().map(PlayerReferenceResolver.ResolvedPlayer::name).toList());
    return context.acknowledge(
        "📊 Fetching statistics for " + request.players().size() + " player(s)…",
        threadPrefix + ": " + names);
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
      Request request,
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

  /** Clamps rather than rejects: a user asking for 100 rows wants "as many as you can". */
  private int resolveLimit(CommandContext context) {
    int requested = context.getOptionAsInt("limit", DEFAULT_LIMIT);
    if (requested < 1) {
      return DEFAULT_LIMIT;
    }
    return Math.min(requested, MAX_LIMIT);
  }

  private Set<String> optionAsSet(CommandContext context, String name) {
    return new LinkedHashSet<>(context.getOptionAsList(name));
  }

  /**
   * Reports unknown constant names ephemerally, with a "did you mean" for each.
   *
   * @param suggestItems true for items, false for abilities, null for heroes — selects which
   *     suggestion source to consult
   * @return true when something was unknown and the user has been told
   */
  private boolean reportUnknown(
      CommandContext context,
      NameResolution resolution,
      String kind,
      Set<String> requested,
      Boolean suggestItems) {

    if (!resolution.hasUnresolved()) {
      return false;
    }
    List<String> parts = new ArrayList<>();
    for (String unknown : resolution.unresolvedNames()) {
      Optional<String> suggestion =
          suggestItems == null
              ? nameResolver.suggestHero(unknown)
              : suggestItems
                  ? nameResolver.suggestItem(unknown)
                  : nameResolver.suggestAbility(unknown);
      parts.add(
          "`" + unknown + "`" + suggestion.map(s -> " (did you mean `" + s + "`?)").orElse(""));
    }
    context.replyEphemeral("❌ Unknown " + kind + ": " + String.join(", ", parts));
    log.debug(
        "Unknown {} names in /stats: {} of {}", kind, resolution.unresolvedNames(), requested);
    return true;
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
  private String label(StatsPeriod period, StatsPeriod.Range range) {
    if (!range.fellBack()) {
      return period.getDisplayName();
    }
    return period.getDisplayName()
        + " unavailable — showing "
        + StatsPeriod.LAST_30_DAYS.getDisplayName().toLowerCase();
  }
}
