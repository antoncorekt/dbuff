package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;
import com.ako.dbuff.resources.model.AbilityRankingResponse;
import com.ako.dbuff.resources.model.ItemComboStatisticResponse;
import com.ako.dbuff.resources.model.ItemRankingResponse;
import com.ako.dbuff.resources.model.PlayerStatisticResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatsEmbedFormatterTest {

  private final StatsEmbedFormatter formatter = new StatsEmbedFormatter();

  private static PlayerStatisticResponse.PlayerStatisticResponseBuilder stats() {
    return PlayerStatisticResponse.builder()
        .playerId(201613150L)
        .playerName("Tigress")
        .totalMatches(42L)
        .wins(25L)
        .losses(17L)
        .avgWinRate(BigDecimal.valueOf(59.52))
        .avgKda(BigDecimal.valueOf(3.10));
  }

  private static String fieldValue(MessageEmbed embed, String name) {
    return embed.getFields().stream()
        .filter(field -> name.equals(field.getName()))
        .map(MessageEmbed.Field::getValue)
        .findFirst()
        .orElse(null);
  }

  private static List<String> fieldNames(MessageEmbed embed) {
    return embed.getFields().stream().map(MessageEmbed.Field::getName).toList();
  }

  @Test
  void overall_showsRecordAndWinRate() {
    MessageEmbed embed = formatter.formatOverall(stats().build(), "Last 30 days");

    assertThat(fieldValue(embed, "Record")).isEqualTo("25W / 17L");
    assertThat(fieldValue(embed, "Win rate")).isEqualTo("59.52%");
    assertThat(embed.getFooter().getText()).isEqualTo("Last 30 days");
  }

  @Test
  void overall_nullMetricRendersAsDashNotZero() {
    MessageEmbed embed =
        formatter.formatOverall(stats().avgGoldPerMin(null).build(), "Last 7 days");

    assertThat(fieldValue(embed, "Avg GPM"))
        .isEqualTo(StatsEmbedFormatter.ABSENT)
        .isNotEqualTo("0");
  }

  @Test
  void overall_heroFiltered_omitsThePopularHeroesField() {
    MessageEmbed filtered = formatter.formatOverall(stats().heroFiltered(true).build(), "All time");
    MessageEmbed unfiltered =
        formatter.formatOverall(stats().heroFiltered(false).build(), "All time");

    assertThat(fieldNames(filtered)).doesNotContain("Popular heroes");
    assertThat(fieldNames(unfiltered)).contains("Popular heroes");
  }

  @Test
  void heroes_rendersOneLinePerHero() {
    PlayerStatisticResponse response =
        stats()
            .popularHeroes(
                List.of(
                    PlayerStatisticResponse.HeroStatistic.builder()
                        .heroId(74L)
                        .heroName("npc_dota_hero_invoker")
                        .heroPrettyName("Invoker")
                        .pickCount(12L)
                        .winRate(BigDecimal.valueOf(58.33))
                        .build(),
                    PlayerStatisticResponse.HeroStatistic.builder()
                        .heroId(1L)
                        .heroPrettyName("Anti-Mage")
                        .pickCount(5L)
                        .winRate(BigDecimal.valueOf(40.00))
                        .build()))
            .build();

    String table = fieldValue(formatter.formatHeroes(response, "Last 30 days"), "Most played");

    assertThat(table).contains("Invoker").contains("12 games").contains("58.33%");
    assertThat(table).contains("Anti-Mage");
    assertThat(table.lines()).hasSize(2);
  }

  @Test
  void heroes_noData_saysSoRatherThanShowingAnEmptyField() {
    MessageEmbed embed = formatter.formatHeroes(stats().build(), "Last 30 days");

    assertThat(fieldValue(embed, "Most played")).isEqualTo("No hero data.");
  }

  @Test
  void itemRanking_rendersPurchaseTimeAsMinutesAndSeconds() {
    ItemRankingResponse item =
        ItemRankingResponse.builder()
            .itemId(1L)
            .itemPrettyName("Blink Dagger")
            .pickCount(9L)
            .pickRate(BigDecimal.valueOf(21.43))
            .winRate(BigDecimal.valueOf(66.67))
            .avgPurchaseTime(BigDecimal.valueOf(605))
            .avgUseCount(BigDecimal.valueOf(3.20))
            .build();

    String table =
        fieldValue(
            formatter.formatItemRanking("Tigress", List.of(item), "Last 30 days"), "Top items");

    assertThat(table).contains("Blink Dagger").contains("10:05").contains("3.20 uses");
  }

  @Test
  void itemRanking_nullUseCountRendersAsDash() {
    ItemRankingResponse item =
        ItemRankingResponse.builder()
            .itemId(1L)
            .itemPrettyName("Boots")
            .pickCount(9L)
            .avgUseCount(null)
            .build();

    String table =
        fieldValue(
            formatter.formatItemRanking("Tigress", List.of(item), "Last 30 days"), "Top items");

    assertThat(table).contains(StatsEmbedFormatter.ABSENT + " uses").doesNotContain("0.00 uses");
  }

  @Test
  void itemRanking_longTableIsSplitAcrossFieldsRatherThanExceedingTheLimit() {
    List<ItemRankingResponse> many = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      many.add(
          ItemRankingResponse.builder()
              .itemId((long) i)
              .itemPrettyName("A Fairly Long Item Name Number " + i)
              .pickCount(10L)
              .pickRate(BigDecimal.valueOf(12.34))
              .winRate(BigDecimal.valueOf(56.78))
              .avgPurchaseTime(BigDecimal.valueOf(600))
              .avgUseCount(BigDecimal.valueOf(1.50))
              .build());
    }

    MessageEmbed embed = formatter.formatItemRanking("Tigress", many, "All time");

    assertThat(embed.getFields())
        .allSatisfy(
            field ->
                assertThat(field.getValue())
                    .hasSizeLessThanOrEqualTo(StatsEmbedFormatter.MAX_FIELD_LENGTH));
    assertThat(fieldNames(embed)).contains("Top items", "Top items (cont.)");
    // Every row survives the split.
    String all =
        embed.getFields().stream().map(MessageEmbed.Field::getValue).reduce("", String::concat);
    assertThat(all).contains("Number 0").contains("Number 24");
  }

  @Test
  void itemCombo_showsGamesFoundAndPerItemLines() {
    ItemComboStatisticResponse combo =
        ItemComboStatisticResponse.builder()
            .playerId(1L)
            .playerName("Tigress")
            .gamesFound(3L)
            .winRate(BigDecimal.valueOf(66.67))
            .avgKda(BigDecimal.valueOf(4.10))
            .members(
                List.of(
                    ItemComboStatisticResponse.Member.builder()
                        .itemId(1L)
                        .itemPrettyName("Blink Dagger")
                        .avgPurchaseTime(BigDecimal.valueOf(450))
                        .avgUseCount(BigDecimal.valueOf(2.50))
                        .build()))
            .build();

    MessageEmbed embed = formatter.formatItemCombo(combo, "Last 30 days");

    assertThat(fieldValue(embed, "Games with all items")).isEqualTo("3");
    assertThat(fieldValue(embed, "Win rate")).isEqualTo("66.67%");
    assertThat(fieldValue(embed, "Per item")).contains("Blink Dagger").contains("7:30");
  }

  @Test
  void abilityRanking_hasNoPurchaseTimeColumn() {
    AbilityRankingResponse ability =
        AbilityRankingResponse.builder()
            .abilityId(5001L)
            .abilityPrettyName("Quas")
            .pickCount(12L)
            .pickRate(BigDecimal.valueOf(90.00))
            .winRate(BigDecimal.valueOf(55.00))
            .avgUseCount(BigDecimal.valueOf(15.00))
            .build();

    String table =
        fieldValue(
            formatter.formatAbilityRanking("Tigress", List.of(ability), "All time"), "Top skills");

    assertThat(table).contains("Quas").contains("15.00 uses").doesNotContain("buy");
  }

  @Test
  void abilityCombo_zeroGames_stillRendersWithoutNulls() {
    AbilityComboStatisticResponse combo =
        AbilityComboStatisticResponse.builder()
            .playerId(1L)
            .playerName("Tigress")
            .gamesFound(0L)
            .winRate(BigDecimal.ZERO)
            .members(List.of())
            .build();

    MessageEmbed embed = formatter.formatAbilityCombo(combo, "Last 30 days");

    assertThat(fieldValue(embed, "Games with all skills")).isEqualTo("0");
    assertThat(fieldValue(embed, "Avg KDA")).isEqualTo(StatsEmbedFormatter.ABSENT);
    assertThat(fieldValue(embed, "Per skill")).isEqualTo("No per-skill data.");
  }

  @Test
  void aPlayerWithNoNameFallsBackToTheAccountId() {
    MessageEmbed embed =
        formatter.formatOverall(stats().playerName(null).playerId(999L).build(), "All time");

    assertThat(embed.getTitle()).contains("999");
  }
}
