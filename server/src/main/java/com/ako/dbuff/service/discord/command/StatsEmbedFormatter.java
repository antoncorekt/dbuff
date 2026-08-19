package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;
import com.ako.dbuff.resources.model.AbilityRankingResponse;
import com.ako.dbuff.resources.model.ItemComboStatisticResponse;
import com.ako.dbuff.resources.model.ItemRankingResponse;
import com.ako.dbuff.resources.model.PlayerStatisticResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Component;

/**
 * Renders statistics responses as Discord embeds.
 *
 * <p>Separate from the command so the layout can be tested without going near option parsing or
 * thread creation.
 *
 * <p>Two rules run through all of it. A null metric renders as {@value #ABSENT}, never as {@code
 * 0.00} — "no data recorded" and "averaged to zero" are different answers and conflating them
 * misleads. And long tables are split across several fields rather than truncated, because Discord
 * silently rejects an embed whose field value exceeds {@value #MAX_FIELD_LENGTH} characters.
 */
@Component
public class StatsEmbedFormatter {

  /** Shown in place of a metric the database has no data for. */
  static final String ABSENT = "—";

  /** Discord's per-field character limit. */
  static final int MAX_FIELD_LENGTH = 1024;

  private static final int EMBED_COLOR = 0x00AE86;
  private static final int SECONDS_PER_MINUTE = 60;
  private static final int DISPLAY_SCALE = 2;

  /** Overall win/loss/KDA statistics for one player. */
  public MessageEmbed formatOverall(PlayerStatisticResponse stats, String periodLabel) {
    EmbedBuilder embed =
        baseEmbed("📊 " + nameOf(stats.getPlayerName(), stats.getPlayerId()), periodLabel)
            .addField("Matches", String.valueOf(orZero(stats.getTotalMatches())), true)
            .addField("Record", record(stats), true)
            .addField("Win rate", percent(stats.getAvgWinRate()), true)
            .addField("Avg KDA", metric(stats.getAvgKda()), true)
            .addField("Best KDA", metric(stats.getMaxKda()), true)
            .addField("Worst KDA", metric(stats.getMinKda()), true)
            .addField("Avg GPM", metric(stats.getAvgGoldPerMin()), true)
            .addField("Avg XPM", metric(stats.getAvgXpPerMin()), true)
            .addField("Avg last hits", metric(stats.getAvgLastHits()), true);

    // Omitted when hero-filtered: the list degenerates to the one hero already named in the
    // request, which reads as noise.
    if (!Boolean.TRUE.equals(stats.getHeroFiltered())) {
      addTable(embed, "Popular heroes", heroLines(stats.getPopularHeroes()), "No hero data.");
    }
    return embed.build();
  }

  /** The popular-heroes table on its own, for {@code /stats heroes}. */
  public MessageEmbed formatHeroes(PlayerStatisticResponse stats, String periodLabel) {
    EmbedBuilder embed =
        baseEmbed("🦸 Heroes — " + nameOf(stats.getPlayerName(), stats.getPlayerId()), periodLabel)
            .addField("Matches", String.valueOf(orZero(stats.getTotalMatches())), true);

    addTable(embed, "Most played", heroLines(stats.getPopularHeroes()), "No hero data.");
    return embed.build();
  }

  /** Top-N item ranking. */
  public MessageEmbed formatItemRanking(
      String playerName, List<ItemRankingResponse> rankings, String periodLabel) {
    EmbedBuilder embed = baseEmbed("🎒 Items — " + playerName, periodLabel);

    List<String> lines = new ArrayList<>();
    for (ItemRankingResponse item : rankings) {
      lines.add(
          "**"
              + displayName(item.getItemPrettyName(), item.getItemName(), item.getItemId())
              + "** — "
              + orZero(item.getPickCount())
              + " games, "
              + percent(item.getPickRate())
              + " picked, "
              + percent(item.getWinRate())
              + " won, buy "
              + duration(item.getAvgPurchaseTime())
              + ", "
              + metric(item.getAvgUseCount())
              + " uses");
    }
    addTable(embed, "Top items", lines, "No items in this period.");
    return embed.build();
  }

  /** Statistics for games containing every requested item. */
  public MessageEmbed formatItemCombo(ItemComboStatisticResponse combo, String periodLabel) {
    EmbedBuilder embed =
        baseEmbed(
                "🎒 Item combo — " + nameOf(combo.getPlayerName(), combo.getPlayerId()),
                periodLabel)
            .addField("Games with all items", String.valueOf(orZero(combo.getGamesFound())), true)
            .addField("Win rate", percent(combo.getWinRate()), true)
            .addField("Avg KDA", metric(combo.getAvgKda()), true);

    List<String> lines = new ArrayList<>();
    for (ItemComboStatisticResponse.Member member : orEmpty(combo.getMembers())) {
      lines.add(
          "**"
              + displayName(member.getItemPrettyName(), member.getItemName(), member.getItemId())
              + "** — buy "
              + duration(member.getAvgPurchaseTime())
              + ", "
              + metric(member.getAvgUseCount())
              + " uses");
    }
    addTable(embed, "Per item", lines, "No per-item data.");
    return embed.build();
  }

  /** Top-N ability ranking. */
  public MessageEmbed formatAbilityRanking(
      String playerName, List<AbilityRankingResponse> rankings, String periodLabel) {
    EmbedBuilder embed = baseEmbed("✨ Skills — " + playerName, periodLabel);

    List<String> lines = new ArrayList<>();
    for (AbilityRankingResponse ability : rankings) {
      lines.add(
          "**"
              + displayName(
                  ability.getAbilityPrettyName(), ability.getAbilityName(), ability.getAbilityId())
              + "** — "
              + orZero(ability.getPickCount())
              + " games, "
              + percent(ability.getPickRate())
              + " picked, "
              + percent(ability.getWinRate())
              + " won, "
              + metric(ability.getAvgUseCount())
              + " uses");
    }
    addTable(embed, "Top skills", lines, "No skills in this period.");
    return embed.build();
  }

  /** Statistics for games containing every requested ability. */
  public MessageEmbed formatAbilityCombo(AbilityComboStatisticResponse combo, String periodLabel) {
    EmbedBuilder embed =
        baseEmbed(
                "✨ Skill combo — " + nameOf(combo.getPlayerName(), combo.getPlayerId()),
                periodLabel)
            .addField("Games with all skills", String.valueOf(orZero(combo.getGamesFound())), true)
            .addField("Win rate", percent(combo.getWinRate()), true)
            .addField("Avg KDA", metric(combo.getAvgKda()), true);

    List<String> lines = new ArrayList<>();
    for (AbilityComboStatisticResponse.Member member : orEmpty(combo.getMembers())) {
      lines.add(
          "**"
              + displayName(
                  member.getAbilityPrettyName(), member.getAbilityName(), member.getAbilityId())
              + "** — "
              + metric(member.getAvgUseCount())
              + " uses");
    }
    addTable(embed, "Per skill", lines, "No per-skill data.");
    return embed.build();
  }

  private EmbedBuilder baseEmbed(String title, String periodLabel) {
    return new EmbedBuilder().setTitle(title).setFooter(periodLabel).setColor(EMBED_COLOR);
  }

  private List<String> heroLines(List<PlayerStatisticResponse.HeroStatistic> heroes) {
    List<String> lines = new ArrayList<>();
    for (PlayerStatisticResponse.HeroStatistic hero : orEmpty(heroes)) {
      lines.add(
          "**"
              + displayName(hero.getHeroPrettyName(), hero.getHeroName(), hero.getHeroId())
              + "** — "
              + orZero(hero.getPickCount())
              + " games, "
              + percent(hero.getWinRate())
              + " won");
    }
    return lines;
  }

  /**
   * Adds {@code lines} as one or more fields, each within Discord's field limit.
   *
   * <p>Splitting rather than truncating: a 25-row table can exceed 1024 characters, and Discord
   * rejects the whole embed rather than trimming it, so a truncating formatter would turn a large
   * request into no answer at all.
   */
  private void addTable(EmbedBuilder embed, String title, List<String> lines, String emptyMessage) {
    if (lines.isEmpty()) {
      embed.addField(title, emptyMessage, false);
      return;
    }

    StringBuilder chunk = new StringBuilder();
    int fieldCount = 0;
    for (String line : lines) {
      if (chunk.length() + line.length() + 1 > MAX_FIELD_LENGTH) {
        embed.addField(fieldCount == 0 ? title : title + " (cont.)", chunk.toString(), false);
        fieldCount++;
        chunk.setLength(0);
      }
      if (chunk.length() > 0) {
        chunk.append('\n');
      }
      chunk.append(line);
    }
    embed.addField(fieldCount == 0 ? title : title + " (cont.)", chunk.toString(), false);
  }

  private String record(PlayerStatisticResponse stats) {
    return orZero(stats.getWins()) + "W / " + orZero(stats.getLosses()) + "L";
  }

  /** A metric, or {@value #ABSENT} when the database recorded none. */
  private String metric(BigDecimal value) {
    return value == null ? ABSENT : scaled(value);
  }

  private String percent(BigDecimal value) {
    return value == null ? ABSENT : scaled(value) + "%";
  }

  /**
   * Two decimal places regardless of the scale the caller happened to build.
   *
   * <p>The repositories all produce scale 2, but echoing the incoming scale would make display
   * depend on how a value was constructed — {@code BigDecimal.valueOf(3.20)} has scale 1 and would
   * print as {@code 3.2} beside a neighbouring {@code 3.20}.
   */
  private String scaled(BigDecimal value) {
    return value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString();
  }

  /** Seconds as {@code m:ss}, since "buy at 400" means nothing to a reader. */
  private String duration(BigDecimal seconds) {
    if (seconds == null) {
      return ABSENT;
    }
    long total = seconds.longValue();
    return total / SECONDS_PER_MINUTE + ":" + String.format("%02d", total % SECONDS_PER_MINUTE);
  }

  private long orZero(Long value) {
    return value == null ? 0L : value;
  }

  private <T> List<T> orEmpty(List<T> values) {
    return values == null ? List.of() : values;
  }

  private String nameOf(String playerName, Long playerId) {
    return playerName != null && !playerName.isBlank() ? playerName : String.valueOf(playerId);
  }

  /** Prefers the human-readable name, falling back through the internal name to the raw ID. */
  private String displayName(String prettyName, String internalName, Long id) {
    if (prettyName != null && !prettyName.isBlank()) {
      return prettyName;
    }
    if (internalName != null && !internalName.isBlank()) {
      return internalName;
    }
    return String.valueOf(id);
  }
}
