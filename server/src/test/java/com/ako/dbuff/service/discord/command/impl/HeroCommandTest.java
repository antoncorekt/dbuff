package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.resources.model.AbilityRankingResponse;
import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class HeroCommandTest {

  private static final Long TIGRESS = 201613150L;
  private static final Long PASTUKH = 204429164L;

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
  private HeroCommand command;

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

    command =
        new HeroCommand(
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
            List.of(
                ItemRankingResponse.builder().itemId(1L).itemPrettyName("Blink Dagger").build()));
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
        .totalMatches(12L)
        .wins(8L)
        .losses(4L)
        .avgWinRate(BigDecimal.valueOf(66.67))
        .build();
  }

  /** Resolves the hero option so {@code prepare} treats "Invoker" as a known hero. */
  private void invokerIsKnown() {
    Mockito.when(nameResolver.resolveHeroes(Set.of("Invoker")))
        .thenReturn(new NameResolution(Set.of(74L), Set.of()));
  }

  private static FakeCommandContext.Builder request() {
    return FakeCommandContext.builder().option("hero", "Invoker").option("player", "Tigress");
  }

  // ---------------------------------------------------------------- definition

  @Test
  void definitionHasNoSubcommandsAndRequiresOnlyTheHero() {
    assertThat(command.getDefinition().getSubcommands()).isEmpty();
    assertThat(command.getDefinition().getOptions())
        .filteredOn(option -> option.isRequired())
        .extracting(option -> option.getName())
        .containsExactly("hero");
  }

  @Test
  void definitionOffersItemsSkillsPeriodAndGameMode() {
    assertThat(command.getDefinition().getOptions())
        .extracting(option -> option.getName())
        .contains("items", "skills", "period", "game_mode", "limit");
  }

  // ---------------------------------------------------------------- validation

  @Test
  void noHeroNamed_repliesEphemerallyAndDoesNoWork() {
    FakeCommandContext context = FakeCommandContext.builder().option("player", "Tigress").build();

    command.execute(null, context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(playerStatisticService);
  }

  @Test
  void unregisteredChannel_isReportedRatherThanQueried() {
    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.empty());

    FakeCommandContext context = request().build();
    command.execute(null, context);

    assertThat(context.getEphemeralReplies().get(0)).contains("/dbuff register");
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void unknownHero_isRejectedWithASuggestionBeforeAcknowledging() {
    Mockito.when(nameResolver.resolveHeroes(Set.of("Invokr")))
        .thenReturn(new NameResolution(Set.of(), Set.of("Invokr")));
    Mockito.when(nameResolver.suggestHero("Invokr")).thenReturn(Optional.of("Invoker"));

    FakeCommandContext context =
        FakeCommandContext.builder().option("hero", "Invokr").option("player", "Tigress").build();
    command.execute(null, context);

    assertThat(context.getEphemeralReplies().get(0)).contains("Invokr").contains("Invoker");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(playerStatisticService);
  }

  @Test
  void tooManyPlayers_isRejectedBeforeAnyAggregation() {
    invokerIsKnown();
    List<PlayerReferenceResolver.ResolvedPlayer> tooMany = new java.util.ArrayList<>();
    for (int i = 0; i <= StatsRequestResolver.MAX_PLAYERS; i++) {
      tooMany.add(new PlayerReferenceResolver.ResolvedPlayer((long) i, "P" + i));
    }
    Mockito.when(playerResolver.resolve(Mockito.anyString(), Mockito.anyList()))
        .thenReturn(new PlayerReferenceResolver.Resolution(tooMany, List.of()));

    FakeCommandContext context = request().build();
    command.execute(null, context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    Mockito.verifyNoInteractions(playerStatisticService);
  }

  // -------------------------------------------------------------------- report

  @Test
  void withoutItemsOrSkills_asksOnlyForTheOverallNumbers() {
    invokerIsKnown();
    FakeCommandContext context = request().build();

    command.execute(null, context);

    Mockito.verify(playerStatisticService)
        .getPlayerStatistics(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.isNull(),
            Mockito.eq(Set.of("Invoker")),
            Mockito.eq(Set.of("game_mode_ability_draft")));
    Mockito.verifyNoInteractions(itemRankingService, abilityRankingService);

    assertThat(context.getEmbeds()).hasSize(1);
    assertThat(context.getEmbeds().get(0).getFields())
        .extracting(field -> field.getName())
        .doesNotContain("Top items", "Top skills");
  }

  @Test
  void itemsOption_addsTheItemTableScopedToTheHero() {
    invokerIsKnown();
    FakeCommandContext context = request().option("items", "true").build();

    command.execute(null, context);

    Mockito.verify(itemRankingService)
        .getItemRankings(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.eq(Set.of("Invoker")),
            Mockito.eq(Set.of("game_mode_ability_draft")),
            Mockito.eq(StatsRequestResolver.DEFAULT_LIMIT));
    Mockito.verifyNoInteractions(abilityRankingService);

    assertThat(context.getEmbeds().get(0).getFields())
        .extracting(field -> field.getName())
        .contains("Top items")
        .doesNotContain("Top skills");
  }

  @Test
  void skillsOption_addsTheSkillTableScopedToTheHero() {
    invokerIsKnown();
    FakeCommandContext context = request().option("skills", "true").build();

    command.execute(null, context);

    Mockito.verify(abilityRankingService)
        .getAbilityRankings(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.eq(Set.of("Invoker")),
            Mockito.eq(Set.of("game_mode_ability_draft")),
            Mockito.eq(StatsRequestResolver.DEFAULT_LIMIT));
    Mockito.verifyNoInteractions(itemRankingService);

    assertThat(context.getEmbeds().get(0).getFields())
        .extracting(field -> field.getName())
        .contains("Top skills")
        .doesNotContain("Top items");
  }

  /** Both tables belong in one embed: three per player would collapse a five-player request. */
  @Test
  void bothOptions_produceOneEmbedCarryingBothTables() {
    invokerIsKnown();
    FakeCommandContext context = request().option("items", "true").option("skills", "true").build();

    command.execute(null, context);

    assertThat(context.getEmbeds()).hasSize(1);
    assertThat(context.getEmbeds().get(0).getFields())
        .extracting(field -> field.getName())
        .contains("Top items", "Top skills");
  }

  @Test
  void embedTitleNamesTheHeroAndThePlayer() {
    invokerIsKnown();
    FakeCommandContext context = request().build();

    command.execute(null, context);

    assertThat(context.getEmbeds().get(0).getTitle()).contains("Invoker").contains("Tigress");
    assertThat(context.getAcknowledgeSummary()).contains("Invoker").contains("Tigress");
    assertThat(context.getAcknowledgeThreadName()).contains("Invoker");
  }

  @Test
  void footerNamesThePeriodAndTheGameMode() {
    invokerIsKnown();
    FakeCommandContext context = request().build();

    command.execute(null, context);

    assertThat(context.getEmbeds().get(0).getFooter().getText())
        .contains("Last 30 days")
        .contains("Ability Draft");
  }

  @Test
  void severalPlayers_getOneEmbedEach() {
    invokerIsKnown();
    resolvesTo(
        new PlayerReferenceResolver.ResolvedPlayer(TIGRESS, "Tigress"),
        new PlayerReferenceResolver.ResolvedPlayer(PASTUKH, "Пастух лолей"));

    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("hero", "Invoker")
            .option("player", "Tigress,Пастух лолей")
            .build();
    command.execute(null, context);

    assertThat(context.getEmbeds()).hasSize(2);
    assertThat(context.getFailures()).isEmpty();
  }

  @Test
  void onePlayerFailing_stillAnswersForTheOthers() {
    invokerIsKnown();
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
        FakeCommandContext.builder()
            .option("hero", "Invoker")
            .option("player", "Tigress,Пастух лолей")
            .build();
    command.execute(null, context);

    assertThat(context.getFailures()).hasSize(1);
    assertThat(context.getFailures().get(0)).contains("Tigress").contains("Invoker");
    assertThat(context.getEmbeds()).hasSize(1);
  }

  // ------------------------------------------------------------------- options

  @Test
  void gameModeOption_overridesTheAbilityDraftDefault() {
    invokerIsKnown();
    Mockito.when(gameModeResolver.resolveOrDefault(Set.of("all")))
        .thenReturn(GameModeSelection.allModes());

    FakeCommandContext context = request().option("game_mode", "all").build();
    command.execute(null, context);

    ArgumentCaptor<Set<String>> modes = ArgumentCaptor.forClass(Set.class);
    Mockito.verify(playerStatisticService)
        .getPlayerStatistics(
            Mockito.eq(TIGRESS),
            Mockito.any(),
            Mockito.any(),
            Mockito.isNull(),
            Mockito.anySet(),
            modes.capture());

    assertThat(modes.getValue()).isEmpty();
    assertThat(context.getEmbeds().get(0).getFooter().getText()).contains("All modes");
  }

  @Test
  void periodOption_isForwardedAsADateRange() {
    invokerIsKnown();
    FakeCommandContext context = request().option("period", "last_3_months").build();

    command.execute(null, context);

    Mockito.verify(playerStatisticService)
        .getPlayerStatistics(
            Mockito.eq(TIGRESS),
            Mockito.eq(LocalDate.now().minusMonths(3)),
            Mockito.eq(LocalDate.now()),
            Mockito.isNull(),
            Mockito.anySet(),
            Mockito.anySet());
    assertThat(context.getEmbeds().get(0).getFooter().getText()).contains("Last 3 months");
  }

  @Test
  void limitAboveTwentyFiveIsClampedNotRejected() {
    invokerIsKnown();
    FakeCommandContext context = request().option("items", "true").option("limit", "100").build();

    command.execute(null, context);

    assertThat(context.getEphemeralReplies()).isEmpty();
    Mockito.verify(itemRankingService)
        .getItemRankings(
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.anySet(),
            Mockito.anySet(),
            Mockito.eq(StatsRequestResolver.MAX_LIMIT));
  }

  /** Discord submits {@code true}; a hand-typed option should not silently mean "off". */
  @Test
  void booleanOptionsAcceptWhatDiscordAndHumansSubmit() {
    invokerIsKnown();

    for (String enabled : List.of("true", "True", "yes", "1")) {
      Mockito.clearInvocations(itemRankingService);
      command.execute(null, request().option("items", enabled).build());

      Mockito.verify(itemRankingService)
          .getItemRankings(
              Mockito.any(),
              Mockito.any(),
              Mockito.any(),
              Mockito.any(),
              Mockito.any(),
              Mockito.anySet(),
              Mockito.anySet(),
              Mockito.anyInt());
    }
  }

  @Test
  void falseBooleanOption_leavesTheTableOut() {
    invokerIsKnown();

    command.execute(null, request().option("items", "false").build());

    Mockito.verifyNoInteractions(itemRankingService);
  }

  // ------------------------------------------------------- defaulting the player

  @Test
  void noPlayerNamed_reportsOnTheWholeFocusGroup() {
    invokerIsKnown();
    Mockito.when(playerResolver.focusGroup(Mockito.anyString()))
        .thenReturn(
            List.of(
                new PlayerReferenceResolver.ResolvedPlayer(TIGRESS, "Tigress"),
                new PlayerReferenceResolver.ResolvedPlayer(PASTUKH, "Пастух лолей")));

    FakeCommandContext context = FakeCommandContext.builder().option("hero", "Invoker").build();
    command.execute(null, context);

    assertThat(context.getEphemeralReplies()).isEmpty();
    assertThat(context.getEmbeds()).hasSize(2);
    Mockito.verify(playerResolver, Mockito.never()).resolve(Mockito.anyString(), Mockito.anyList());
  }

  @Test
  void noPlayerNamedAndNothingTracked_saysToAddPlayersRatherThanToRegister() {
    invokerIsKnown();
    Mockito.when(playerResolver.focusGroup(Mockito.anyString())).thenReturn(List.of());

    FakeCommandContext context = FakeCommandContext.builder().option("hero", "Invoker").build();
    command.execute(null, context);

    assertThat(context.getEphemeralReplies().get(0)).contains("/dbuff add");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(playerStatisticService);
  }
}
