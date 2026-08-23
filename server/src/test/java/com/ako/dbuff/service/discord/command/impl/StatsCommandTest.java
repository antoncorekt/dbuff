package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;
import com.ako.dbuff.resources.model.AbilityRankingResponse;
import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.ItemComboStatisticResponse;
import com.ako.dbuff.resources.model.ItemRankingResponse;
import com.ako.dbuff.resources.model.PlayerStatisticResponse;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.CurrentPatchDateResolver;
import com.ako.dbuff.service.constant.GameModeResolver;
import com.ako.dbuff.service.constant.GameModeSelection;
import com.ako.dbuff.service.constant.NameResolution;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver;
import com.ako.dbuff.service.discord.command.StatsEmbedFormatter;
import com.ako.dbuff.service.discord.command.StatsRequestResolver;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.ranking.AbilityRankingService;
import com.ako.dbuff.service.ranking.ItemRankingService;
import com.ako.dbuff.service.ranking.PlayerStatisticService;
import com.ako.dbuff.service.ranking.StatsPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class StatsCommandTest {

  private static final Long TIGRESS = 201613150L;
  private static final Long PASTUKH = 204429164L;

  /** What {@code GameModeResolver} returns for the default, absent {@code game_mode:} option. */
  private static final GameModeSelection ABILITY_DRAFT =
      new GameModeSelection(
          Set.of("game_mode_ability_draft"), Set.of(18L), Set.of(), "Ability Draft");

  private DbufInstanceConfigService instanceConfigService;
  private PlayerReferenceResolver playerResolver;
  private ConstantNameResolver nameResolver;
  private GameModeResolver gameModeResolver;
  private CurrentPatchDateResolver patchDateResolver;
  private PlayerStatisticService playerStatisticService;
  private ItemRankingService itemRankingService;
  private AbilityRankingService abilityRankingService;
  private StatsCommand command;

  @BeforeEach
  void setUp() {
    instanceConfigService = Mockito.mock(DbufInstanceConfigService.class);
    playerResolver = Mockito.mock(PlayerReferenceResolver.class);
    nameResolver = Mockito.mock(ConstantNameResolver.class);
    gameModeResolver = Mockito.mock(GameModeResolver.class);
    patchDateResolver = Mockito.mock(CurrentPatchDateResolver.class);
    playerStatisticService = Mockito.mock(PlayerStatisticService.class);
    itemRankingService = Mockito.mock(ItemRankingService.class);
    abilityRankingService = Mockito.mock(AbilityRankingService.class);

    // The real resolver, not a mock: it owns the validate-before-acknowledge ordering these
    // tests exist to pin down, and a mock would assert nothing about it.
    command =
        new StatsCommand(
            new StatsRequestResolver(
                instanceConfigService,
                playerResolver,
                nameResolver,
                gameModeResolver,
                patchDateResolver),
            playerStatisticService,
            itemRankingService,
            abilityRankingService,
            new StatsEmbedFormatter());

    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.of(DbufInstanceConfigResponse.builder().id("instance-1").build()));
    resolvesTo(new PlayerReferenceResolver.ResolvedPlayer(TIGRESS, "Tigress"));
    Mockito.when(nameResolver.resolveHeroes(Mockito.anySet())).thenReturn(NameResolution.empty());
    Mockito.when(nameResolver.resolveItems(Mockito.anySet())).thenReturn(NameResolution.empty());
    Mockito.when(nameResolver.resolveAbilities(Mockito.anySet()))
        .thenReturn(NameResolution.empty());
    Mockito.when(gameModeResolver.resolveOrDefault(Mockito.anySet())).thenReturn(ABILITY_DRAFT);
    Mockito.when(patchDateResolver.getCurrentPatchStartDate())
        .thenReturn(Optional.of(LocalDate.of(2026, 8, 1)));
    Mockito.when(
            playerStatisticService.getPlayerStatistics(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(stats());
    Mockito.when(
            itemRankingService.getItemRankings(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            List.of(ItemRankingResponse.builder().itemId(1L).itemPrettyName("Blink").build()));
    Mockito.when(
            abilityRankingService.getAbilityRankings(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            List.of(
                AbilityRankingResponse.builder().abilityId(1L).abilityPrettyName("Quas").build()));
  }

  private void resolvesTo(PlayerReferenceResolver.ResolvedPlayer... players) {
    Mockito.when(playerResolver.resolve(Mockito.anyString(), Mockito.anyList()))
        .thenReturn(new PlayerReferenceResolver.Resolution(List.of(players), List.of()));
  }

  private static PlayerStatisticResponse stats() {
    return PlayerStatisticResponse.builder()
        .playerId(TIGRESS)
        .playerName("Tigress")
        .totalMatches(42L)
        .wins(25L)
        .losses(17L)
        .avgWinRate(BigDecimal.valueOf(59.52))
        .build();
  }

  private static FakeCommandContext context() {
    return FakeCommandContext.builder().option("player", "Tigress").build();
  }

  // ---------------------------------------------------------------- definition

  @Test
  void definitionExposesTheFourSubcommands() {
    assertThat(command.getDefinition().getSubcommands())
        .extracting(subcommand -> subcommand.getName())
        .containsExactlyInAnyOrder("overall", "heroes", "items", "skills");
  }

  @Test
  void periodOptionOffersEveryPresetAsAStaticChoice() {
    assertThat(
            command.getDefinition().getSubcommands().stream()
                .filter(s -> s.getName().equals("overall"))
                .findFirst()
                .orElseThrow()
                .getOptions()
                .stream()
                .filter(o -> o.getName().equals("period"))
                .findFirst()
                .orElseThrow()
                .getChoices())
        .hasSize(StatsPeriod.values().length);
  }

  // ---------------------------------------------------------------- validation

  @Test
  void unregisteredChannel_repliesEphemerallyAndDoesNoWork() {
    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.empty());
    FakeCommandContext context = context();

    command.execute("overall", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getEphemeralReplies().get(0)).contains("/dbuff register");
    assertThat(context.getAcknowledgeSummary()).isNull();
    assertThat(context.getEmbeds()).isEmpty();
  }

  @Test
  void noPlayerNamedAndNothingTracked_saysToAddPlayersRatherThanToRegister() {
    Mockito.when(playerResolver.focusGroup(Mockito.anyString())).thenReturn(List.of());
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("overall", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getEphemeralReplies().get(0)).contains("/dbuff add");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(playerStatisticService);
  }

  @Test
  void unknownPlayer_isNamedWithASuggestionAndNoThreadIsCreated() {
    Mockito.when(playerResolver.resolve(Mockito.anyString(), Mockito.anyList()))
        .thenReturn(new PlayerReferenceResolver.Resolution(List.of(), List.of("Tigres")));
    Mockito.when(playerResolver.suggest(Mockito.anyString(), Mockito.eq("Tigres")))
        .thenReturn(Optional.of("Tigress"));

    FakeCommandContext context = FakeCommandContext.builder().option("player", "Tigres").build();
    command.execute("overall", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("Tigres").contains("Tigress");
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void moreThanFivePlayers_isRejectedBeforeAnyAggregation() {
    List<PlayerReferenceResolver.ResolvedPlayer> six = new ArrayList<>();
    for (int i = 0; i < StatsRequestResolver.MAX_PLAYERS + 1; i++) {
      six.add(new PlayerReferenceResolver.ResolvedPlayer((long) i, "P" + i));
    }
    Mockito.when(playerResolver.resolve(Mockito.anyString(), Mockito.anyList()))
        .thenReturn(new PlayerReferenceResolver.Resolution(six, List.of()));

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "P0,P1,P2,P3,P4,P5").build();
    command.execute("overall", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(playerStatisticService);
  }

  @Test
  void unknownHero_isRejectedWithASuggestionBeforeAcknowledging() {
    Mockito.when(nameResolver.resolveHeroes(Set.of("Invokr")))
        .thenReturn(new NameResolution(Set.of(), Set.of("Invokr")));
    Mockito.when(nameResolver.suggestHero("Invokr")).thenReturn(Optional.of("Invoker"));

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("hero", "Invokr").build();
    command.execute("overall", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("Invokr").contains("Invoker");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(playerStatisticService);
  }

  // ------------------------------------------------------------------- overall

  @Test
  void overall_happyPathWithTwoPlayers_acknowledgesOnceAndPostsTwoEmbeds() {
    resolvesTo(
        new PlayerReferenceResolver.ResolvedPlayer(TIGRESS, "Tigress"),
        new PlayerReferenceResolver.ResolvedPlayer(PASTUKH, "Пастух лолей"));
    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress,Пастух лолей").build();

    command.execute("overall", context);

    assertThat(context.getAcknowledgeSummary()).contains("Tigress", "Пастух лолей");
    assertThat(context.getEmbeds()).hasSize(2);
    assertThat(context.getFailures()).isEmpty();
  }

  /**
   * Every subcommand used to acknowledge with "Fetching statistics for N player(s)", so a thread
   * carried no record of which question produced it — and the threads outlive the invocation.
   */
  @Test
  void eachSubcommandAcknowledgesWithItsOwnTitleAndScope() {
    assertThat(summaryOf("overall"))
        .contains("Overall stats", "Tigress", "Last 30 days", "Ability Draft");
    assertThat(summaryOf("heroes")).contains("Most played heroes");
    assertThat(summaryOf("items")).contains("Item stats");
    assertThat(summaryOf("skills")).contains("Skill stats");
  }

  @Test
  void threadNameNamesTheSubcommandAndThePlayers() {
    FakeCommandContext context = context();

    command.execute("items", context);

    assertThat(context.getAcknowledgeThreadName()).contains("Item stats").contains("Tigress");
  }

  @Test
  void itemCombo_namesTheComboInTheTitle() {
    Mockito.when(nameResolver.resolveItems(Set.of("Blink Dagger")))
        .thenReturn(new NameResolution(Set.of(1L), Set.of()));
    Mockito.when(
            itemRankingService.getItemComboStatistics(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            ItemComboStatisticResponse.builder()
                .playerId(TIGRESS)
                .playerName("Tigress")
                .gamesFound(3L)
                .winRate(BigDecimal.valueOf(66.67))
                .members(List.of())
                .build());

    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("items", "Blink Dagger")
            .build();
    command.execute("items", context);

    assertThat(context.getAcknowledgeSummary()).contains("Item combo").contains("Blink Dagger");
  }

  private String summaryOf(String subcommand) {
    FakeCommandContext context = context();
    command.execute(subcommand, context);
    return context.getAcknowledgeSummary();
  }

  @Test
  void overall_onePlayerFailing_stillAnswersForTheOthers() {
    resolvesTo(
        new PlayerReferenceResolver.ResolvedPlayer(TIGRESS, "Tigress"),
        new PlayerReferenceResolver.ResolvedPlayer(PASTUKH, "Пастух лолей"));
    Mockito.when(
            playerStatisticService.getPlayerStatistics(
                Mockito.eq(TIGRESS),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenThrow(new IllegalStateException("database went away"));

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress,Пастух лолей").build();
    command.execute("overall", context);

    assertThat(context.getFailures()).hasSize(1);
    assertThat(context.getFailures().get(0)).contains("Tigress");
    assertThat(context.getEmbeds()).hasSize(1);
  }

  @Test
  void overall_forwardsTheHeroFilterAndTheResolvedDateRange() {
    Mockito.when(nameResolver.resolveHeroes(Set.of("Invoker")))
        .thenReturn(new NameResolution(Set.of(74L), Set.of()));
    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("hero", "Invoker")
            .option("period", "last_7_days")
            .build();

    command.execute("overall", context);

    ArgumentCaptor<LocalDate> start = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<Set<String>> heroes = ArgumentCaptor.forClass(Set.class);
    Mockito.verify(playerStatisticService)
        .getPlayerStatistics(
            Mockito.eq(TIGRESS),
            start.capture(),
            Mockito.eq(LocalDate.now()),
            Mockito.isNull(),
            heroes.capture(),
            Mockito.any());

    assertThat(start.getValue()).isEqualTo(LocalDate.now().minusDays(7));
    assertThat(heroes.getValue()).containsExactly("Invoker");
  }

  @Test
  void overall_allTime_passesNoLowerBound() {
    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("period", "all_time")
            .build();

    command.execute("overall", context);

    Mockito.verify(playerStatisticService)
        .getPlayerStatistics(
            Mockito.eq(TIGRESS),
            Mockito.isNull(),
            Mockito.eq(LocalDate.now()),
            Mockito.isNull(),
            Mockito.anySet(),
            Mockito.any());
  }

  @Test
  void overall_patchDateUnavailable_saysSoInTheFooterRatherThanClaimingCurrentPatch() {
    Mockito.when(patchDateResolver.getCurrentPatchStartDate()).thenReturn(Optional.empty());
    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("period", "current_patch")
            .build();

    command.execute("overall", context);

    assertThat(context.getEmbeds()).hasSize(1);
    assertThat(context.getEmbeds().get(0).getFooter().getText())
        .contains("unavailable")
        .contains("last 30 days");
  }

  @Test
  void overall_heroFilteredResponse_omitsThePopularHeroesField() {
    Mockito.when(
            playerStatisticService.getPlayerStatistics(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            PlayerStatisticResponse.builder()
                .playerId(TIGRESS)
                .playerName("Tigress")
                .totalMatches(5L)
                .heroFiltered(true)
                .build());
    Mockito.when(nameResolver.resolveHeroes(Set.of("Invoker")))
        .thenReturn(new NameResolution(Set.of(74L), Set.of()));

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("hero", "Invoker").build();
    command.execute("overall", context);

    assertThat(context.getEmbeds().get(0).getFields())
        .extracting(field -> field.getName())
        .doesNotContain("Popular heroes");
  }

  // -------------------------------------------------------------------- heroes

  @Test
  void heroes_offersAnOptionalHeroFilter() {
    OptionData hero =
        command.getDefinition().getSubcommands().stream()
            .filter(subcommand -> subcommand.getName().equals("heroes"))
            .findFirst()
            .orElseThrow()
            .getOptions()
            .stream()
            .filter(option -> option.getName().equals("hero"))
            .findFirst()
            .orElseThrow();

    assertThat(hero.isRequired()).isFalse();
    assertThat(hero.isAutoComplete()).isTrue();
  }

  @Test
  void heroes_heroFilter_isForwardedSoTheTableCollapsesToThatHero() {
    Mockito.when(nameResolver.resolveHeroes(Set.of("Invoker")))
        .thenReturn(new NameResolution(Set.of(74L), Set.of()));

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("hero", "Invoker").build();
    command.execute("heroes", context);

    Mockito.verify(playerStatisticService)
        .getPlayerStatistics(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(StatsRequestResolver.DEFAULT_LIMIT),
            Mockito.eq(Set.of("Invoker")),
            Mockito.anySet());
    assertThat(context.getEphemeralReplies()).isEmpty();
  }

  /**
   * Unlike {@code /stats overall}, which drops the hero list once filtered, here the table is the
   * answer — omitting it would leave an embed with nothing but a match count.
   */
  @Test
  void heroes_heroFiltered_stillRendersTheTable() {
    Mockito.when(
            playerStatisticService.getPlayerStatistics(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            PlayerStatisticResponse.builder()
                .playerId(TIGRESS)
                .playerName("Tigress")
                .totalMatches(12L)
                .heroFiltered(true)
                .popularHeroes(
                    List.of(
                        PlayerStatisticResponse.HeroStatistic.builder()
                            .heroId(74L)
                            .heroPrettyName("Invoker")
                            .pickCount(12L)
                            .winRate(BigDecimal.valueOf(66.67))
                            .build()))
                .build());
    Mockito.when(nameResolver.resolveHeroes(Set.of("Invoker")))
        .thenReturn(new NameResolution(Set.of(74L), Set.of()));

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("hero", "Invoker").build();
    command.execute("heroes", context);

    assertThat(context.getEmbeds().get(0).getFields())
        .anySatisfy(
            field -> {
              assertThat(field.getName()).isEqualTo("Most played");
              assertThat(field.getValue()).contains("Invoker").contains("66.67%");
            });
  }

  @Test
  void heroes_unknownHero_isRejectedBeforeAcknowledging() {
    Mockito.when(nameResolver.resolveHeroes(Set.of("Invokr")))
        .thenReturn(new NameResolution(Set.of(), Set.of("Invokr")));
    Mockito.when(nameResolver.suggestHero("Invokr")).thenReturn(Optional.of("Invoker"));

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("hero", "Invokr").build();
    command.execute("heroes", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("Invokr").contains("Invoker");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(playerStatisticService);
  }

  @Test
  void heroes_limitAboveTwentyFiveIsClampedNotRejected() {
    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("limit", "100").build();

    command.execute("heroes", context);

    assertThat(context.getEphemeralReplies()).isEmpty();
    Mockito.verify(playerStatisticService)
        .getPlayerStatistics(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(StatsRequestResolver.MAX_LIMIT),
            Mockito.anySet(),
            Mockito.any());
  }

  @Test
  void heroes_limitBelowOneFallsBackToTheDefault() {
    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("limit", "0").build();

    command.execute("heroes", context);

    Mockito.verify(playerStatisticService)
        .getPlayerStatistics(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(StatsRequestResolver.DEFAULT_LIMIT),
            Mockito.anySet(),
            Mockito.any());
  }

  @Test
  void heroes_noGamesInRange_saysSoRatherThanPostingAnEmptyTable() {
    Mockito.when(
            playerStatisticService.getPlayerStatistics(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            PlayerStatisticResponse.builder()
                .playerId(TIGRESS)
                .playerName("Tigress")
                .totalMatches(0L)
                .build());

    FakeCommandContext context = context();
    command.execute("heroes", context);

    assertThat(context.getEmbeds().get(0).getFields())
        .anySatisfy(field -> assertThat(field.getValue()).contains("No hero data"));
  }

  // --------------------------------------------------------------------- items

  @Test
  void items_absent_callsTheRankingServiceAndNotTheComboService() {
    FakeCommandContext context = context();

    command.execute("items", context);

    Mockito.verify(itemRankingService)
        .getItemRankings(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.anySet(),
            Mockito.any(),
            Mockito.eq(StatsRequestResolver.DEFAULT_LIMIT));
    Mockito.verify(itemRankingService, Mockito.never())
        .getItemComboStatistics(
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any());
    assertThat(context.getEmbeds()).hasSize(1);
  }

  @Test
  void items_present_callsTheComboServiceAndNotTheRankingService() {
    Mockito.when(nameResolver.resolveItems(Set.of("Blink Dagger", "Black King Bar")))
        .thenReturn(new NameResolution(Set.of(1L, 2L), Set.of()));
    Mockito.when(
            itemRankingService.getItemComboStatistics(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            ItemComboStatisticResponse.builder()
                .playerId(TIGRESS)
                .playerName("Tigress")
                .gamesFound(3L)
                .winRate(BigDecimal.valueOf(66.67))
                .members(List.of())
                .build());

    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("items", "Blink Dagger, Black King Bar")
            .build();
    command.execute("items", context);

    Mockito.verify(itemRankingService)
        .getItemComboStatistics(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(Set.of("Blink Dagger", "Black King Bar")),
            Mockito.anySet(),
            Mockito.any());
    Mockito.verify(itemRankingService, Mockito.never())
        .getItemRankings(
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any());
    assertThat(context.getEmbeds()).hasSize(1);
  }

  @Test
  void items_unknownName_repliesEphemerallyAndCallsNeitherService() {
    Mockito.when(nameResolver.resolveItems(Set.of("blnk")))
        .thenReturn(new NameResolution(Set.of(), Set.of("blnk")));
    Mockito.when(nameResolver.suggestItem("blnk")).thenReturn(Optional.of("Blink Dagger"));

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("items", "blnk").build();
    command.execute("items", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("blnk").contains("Blink Dagger");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(itemRankingService);
  }

  @Test
  void items_comboWithNoMatchingGames_postsAnExplicitMessageAndNoEmbed() {
    Mockito.when(nameResolver.resolveItems(Set.of("Blink Dagger")))
        .thenReturn(new NameResolution(Set.of(1L), Set.of()));
    Mockito.when(
            itemRankingService.getItemComboStatistics(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            ItemComboStatisticResponse.builder()
                .playerId(TIGRESS)
                .playerName("Tigress")
                .gamesFound(0L)
                .winRate(BigDecimal.ZERO)
                .members(List.of())
                .build());

    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("items", "Blink Dagger")
            .build();
    command.execute("items", context);

    assertThat(context.getEmbeds()).isEmpty();
    assertThat(context.getPosts()).anySatisfy(post -> assertThat(post).contains("no games"));
  }

  /**
   * The channel check has to come first. Told "unknown item" in a channel that was never
   * registered, a user would go hunting for the right item name instead of running {@code /dbuff
   * register}.
   */
  @Test
  void items_unregisteredChannelWithAnItemTypo_reportsTheChannelNotTheTypo() {
    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.empty());
    Mockito.when(nameResolver.resolveItems(Set.of("blnk")))
        .thenReturn(new NameResolution(Set.of(), Set.of("blnk")));

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("items", "blnk").build();
    command.execute("items", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getEphemeralReplies().get(0)).contains("/dbuff register");
  }

  // -------------------------------------------------------------------- skills

  @Test
  void skills_absent_callsTheRankingService() {
    FakeCommandContext context = context();

    command.execute("skills", context);

    Mockito.verify(abilityRankingService)
        .getAbilityRankings(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.anySet(),
            Mockito.any(),
            Mockito.eq(StatsRequestResolver.DEFAULT_LIMIT));
    assertThat(context.getEmbeds()).hasSize(1);
  }

  @Test
  void skills_present_callsTheComboService() {
    Mockito.when(nameResolver.resolveAbilities(Set.of("Quas", "Wex")))
        .thenReturn(new NameResolution(Set.of(5001L, 5002L), Set.of()));
    Mockito.when(
            abilityRankingService.getAbilityComboStatistics(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            AbilityComboStatisticResponse.builder()
                .playerId(TIGRESS)
                .playerName("Tigress")
                .gamesFound(2L)
                .winRate(BigDecimal.valueOf(50.00))
                .members(List.of())
                .build());

    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("skills", "Quas, Wex")
            .build();
    command.execute("skills", context);

    Mockito.verify(abilityRankingService)
        .getAbilityComboStatistics(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(Set.of("Quas", "Wex")),
            Mockito.any(),
            Mockito.anySet(),
            Mockito.any());
    assertThat(context.getEmbeds()).hasSize(1);
  }

  @Test
  void skills_unknownName_repliesEphemerallyAndCallsNoService() {
    Mockito.when(nameResolver.resolveAbilities(Set.of("Quaz")))
        .thenReturn(new NameResolution(Set.of(), Set.of("Quaz")));
    Mockito.when(nameResolver.suggestAbility("Quaz")).thenReturn(Optional.of("Quas"));

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("skills", "Quaz").build();
    command.execute("skills", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("Quaz").contains("Quas");
    Mockito.verifyNoInteractions(abilityRankingService);
  }

  @Test
  void skills_comboWithNoMatchingGames_postsAnExplicitMessage() {
    Mockito.when(nameResolver.resolveAbilities(Set.of("Quas")))
        .thenReturn(new NameResolution(Set.of(5001L), Set.of()));
    Mockito.when(
            abilityRankingService.getAbilityComboStatistics(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            AbilityComboStatisticResponse.builder()
                .playerId(TIGRESS)
                .playerName("Tigress")
                .gamesFound(0L)
                .winRate(BigDecimal.ZERO)
                .members(List.of())
                .build());

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("skills", "Quas").build();
    command.execute("skills", context);

    assertThat(context.getEmbeds()).isEmpty();
    assertThat(context.getPosts()).anySatisfy(post -> assertThat(post).contains("no games"));
  }

  // --------------------------------------------------------- skills plus items

  @Test
  void skillsWithItems_forwardsBothSoTheConjunctionCoversTheSameGame() {
    Mockito.when(nameResolver.resolveAbilities(Set.of("Quas", "Wex")))
        .thenReturn(new NameResolution(Set.of(5001L, 5002L), Set.of()));
    Mockito.when(nameResolver.resolveItems(Set.of("Blink Dagger")))
        .thenReturn(new NameResolution(Set.of(1L), Set.of()));
    abilityComboFound();

    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("skills", "Quas, Wex")
            .option("items", "Blink Dagger")
            .build();
    command.execute("skills", context);

    Mockito.verify(abilityRankingService)
        .getAbilityComboStatistics(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(Set.of("Quas", "Wex")),
            Mockito.eq(Set.of("Blink Dagger")),
            Mockito.anySet(),
            Mockito.anySet());
    assertThat(context.getAcknowledgeSummary()).contains("Skill + item combo");
    assertThat(context.getEmbeds()).hasSize(1);
  }

  @Test
  void skillsWithItems_rendersTheItemTableAlongsideTheSkillTable() {
    Mockito.when(nameResolver.resolveAbilities(Set.of("Quas")))
        .thenReturn(new NameResolution(Set.of(5001L), Set.of()));
    Mockito.when(nameResolver.resolveItems(Set.of("Blink Dagger")))
        .thenReturn(new NameResolution(Set.of(1L), Set.of()));
    Mockito.when(
            abilityRankingService.getAbilityComboStatistics(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            AbilityComboStatisticResponse.builder()
                .playerId(TIGRESS)
                .playerName("Tigress")
                .gamesFound(2L)
                .winRate(BigDecimal.valueOf(50.00))
                .members(List.of())
                .itemMembers(
                    List.of(
                        AbilityComboStatisticResponse.ItemMember.builder()
                            .itemId(1L)
                            .itemPrettyName("Blink Dagger")
                            .avgPurchaseTime(BigDecimal.valueOf(512))
                            .build()))
                .build());

    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("skills", "Quas")
            .option("items", "Blink Dagger")
            .build();
    command.execute("skills", context);

    assertThat(context.getEmbeds().get(0).getFields())
        .extracting(field -> field.getName())
        .contains("Per skill", "Per item");
    assertThat(context.getEmbeds().get(0).getTitle()).contains("Skill + item combo");
  }

  /**
   * Items alone cannot narrow a skill ranking, and answering the unfiltered top-N question instead
   * would look like a valid answer to the question that was asked.
   */
  @Test
  void skills_itemsWithoutSkills_isRejectedRatherThanIgnored() {
    Mockito.when(nameResolver.resolveItems(Set.of("Blink Dagger")))
        .thenReturn(new NameResolution(Set.of(1L), Set.of()));

    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("items", "Blink Dagger")
            .build();
    command.execute("skills", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("/stats items");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(abilityRankingService);
  }

  @Test
  void skillsWithItems_unknownItem_isReportedBeforeAcknowledging() {
    Mockito.when(nameResolver.resolveAbilities(Set.of("Quas")))
        .thenReturn(new NameResolution(Set.of(5001L), Set.of()));
    Mockito.when(nameResolver.resolveItems(Set.of("blnk")))
        .thenReturn(new NameResolution(Set.of(), Set.of("blnk")));
    Mockito.when(nameResolver.suggestItem("blnk")).thenReturn(Optional.of("Blink Dagger"));

    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("skills", "Quas")
            .option("items", "blnk")
            .build();
    command.execute("skills", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("blnk").contains("Blink Dagger");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(abilityRankingService);
  }

  // ----------------------------------------------------------------- game mode

  @Test
  void gameModeDefaultsToAbilityDraftWhenTheOptionIsAbsent() {
    command.execute("overall", context());

    Mockito.verify(gameModeResolver).resolveOrDefault(Set.of());
    ArgumentCaptor<Set<String>> modes = ArgumentCaptor.forClass(Set.class);
    Mockito.verify(playerStatisticService)
        .getPlayerStatistics(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.isNull(),
            Mockito.anySet(),
            modes.capture());

    assertThat(modes.getValue()).containsExactly("game_mode_ability_draft");
  }

  @Test
  void gameModeAcceptsAList() {
    Mockito.when(gameModeResolver.resolveOrDefault(Set.of("ability_draft", "all_pick")))
        .thenReturn(
            new GameModeSelection(
                Set.of("game_mode_ability_draft", "game_mode_all_draft"),
                Set.of(18L, 22L),
                Set.of(),
                "Ability Draft, All Draft"));

    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("game_mode", "ability_draft, all_pick")
            .build();
    command.execute("overall", context);

    ArgumentCaptor<Set<String>> modes = ArgumentCaptor.forClass(Set.class);
    Mockito.verify(playerStatisticService)
        .getPlayerStatistics(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.isNull(),
            Mockito.anySet(),
            modes.capture());

    assertThat(modes.getValue())
        .containsExactlyInAnyOrder("game_mode_ability_draft", "game_mode_all_draft");
    assertThat(context.getEmbeds().get(0).getFooter().getText()).contains("All Draft");
  }

  @Test
  void gameModeAll_passesNoFilterSoEveryModeCounts() {
    Mockito.when(gameModeResolver.resolveOrDefault(Set.of("all")))
        .thenReturn(GameModeSelection.allModes());

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("game_mode", "all").build();
    command.execute("overall", context);

    Mockito.verify(playerStatisticService)
        .getPlayerStatistics(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.isNull(),
            Mockito.anySet(),
            Mockito.eq(Set.of()));
    assertThat(context.getEmbeds().get(0).getFooter().getText()).contains("All modes");
  }

  @Test
  void unknownGameMode_isReportedWithASuggestionBeforeAcknowledging() {
    Mockito.when(gameModeResolver.resolveOrDefault(Set.of("abilty_draft")))
        .thenReturn(new GameModeSelection(Set.of(), Set.of(), Set.of("abilty_draft"), "All modes"));
    Mockito.when(gameModeResolver.suggest("abilty_draft")).thenReturn(Optional.of("Ability Draft"));

    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "Tigress")
            .option("game_mode", "abilty_draft")
            .build();
    command.execute("overall", context);

    assertThat(context.getEphemeralReplies().get(0))
        .contains("abilty_draft")
        .contains("Ability Draft");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(playerStatisticService);
  }

  @Test
  void everySubcommandOffersTheGameModeOption() {
    assertThat(command.getDefinition().getSubcommands())
        .allSatisfy(
            subcommand ->
                assertThat(subcommand.getOptions())
                    .as("options of /stats %s", subcommand.getName())
                    .anySatisfy(option -> assertThat(option.getName()).isEqualTo("game_mode")));
  }

  private void abilityComboFound() {
    Mockito.when(
            abilityRankingService.getAbilityComboStatistics(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(
            AbilityComboStatisticResponse.builder()
                .playerId(TIGRESS)
                .playerName("Tigress")
                .gamesFound(2L)
                .winRate(BigDecimal.valueOf(50.00))
                .members(List.of())
                .itemMembers(List.of())
                .build());
  }

  // ------------------------------------------------------- defaulting the player

  @Test
  void noPlayerNamed_answersForTheWholeFocusGroup() {
    tracks("Tigress", "Пастух лолей");
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("overall", context);

    assertThat(context.getEphemeralReplies()).isEmpty();
    assertThat(context.getEmbeds()).hasSize(2);
    assertThat(context.getAcknowledgeSummary()).contains("Tigress", "Пастух лолей");
    // The reference resolver is for parsing what a user typed; nothing was typed.
    Mockito.verify(playerResolver, Mockito.never()).resolve(Mockito.anyString(), Mockito.anyList());
  }

  @Test
  void namedPlayerStillWinsOverTheGroupDefault() {
    tracks("Tigress", "Пастух лолей", "Someone Else");
    FakeCommandContext context = context();

    command.execute("overall", context);

    assertThat(context.getEmbeds()).hasSize(1);
    Mockito.verify(playerResolver).resolve(Mockito.anyString(), Mockito.anyList());
    Mockito.verify(playerResolver, Mockito.never()).focusGroup(Mockito.anyString());
  }

  /**
   * A group larger than the cap is trimmed rather than refused, so a bare command still answers —
   * but it must say so, since five of eight players reads exactly like all of them.
   */
  @Test
  void groupLargerThanTheCap_isTrimmedAndSaysSo() {
    tracks("A", "B", "C", "D", "E", "F", "G", "H");
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("overall", context);

    assertThat(context.getEmbeds()).hasSize(StatsRequestResolver.MAX_PLAYERS);
    assertThat(context.getAcknowledgeSummary())
        .contains("showing " + StatsRequestResolver.MAX_PLAYERS + " of 8 tracked");
  }

  @Test
  void groupWithinTheCap_saysNothingAboutTrimming() {
    tracks("Tigress", "Пастух лолей");
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("overall", context);

    assertThat(context.getAcknowledgeSummary()).doesNotContain("tracked");
  }

  /** Stubs the channel's focus group, in the order the resolver would return it. */
  private void tracks(String... names) {
    List<PlayerReferenceResolver.ResolvedPlayer> group = new ArrayList<>();
    for (int i = 0; i < names.length; i++) {
      group.add(new PlayerReferenceResolver.ResolvedPlayer((long) (1000 + i), names[i]));
    }
    Mockito.when(playerResolver.focusGroup(Mockito.anyString())).thenReturn(group);
  }
}
