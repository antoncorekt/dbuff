package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.dotapi.api.SearchApi;
import com.ako.dbuff.dotapi.model.SearchResponse;
import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.PlayerInfo;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.google.common.util.concurrent.RateLimiter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the three player-sourced autocomplete providers. */
class PlayerAutocompleteProvidersTest {

  private DbufInstanceConfigService instanceConfigService;
  private TrackedPlayerAutocomplete tracked;

  @BeforeEach
  void setUp() {
    instanceConfigService = Mockito.mock(DbufInstanceConfigService.class);
    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                DbufInstanceConfigResponse.builder()
                    .id("instance-1")
                    .players(
                        Set.of(
                            PlayerInfo.builder().id(204429164L).name("Пастух лолей").build(),
                            PlayerInfo.builder().id(201613150L).name("Tigress").build()))
                    .build()));
    tracked = new TrackedPlayerAutocomplete(instanceConfigService);
  }

  @Test
  void trackedPlayer_servesTheStatsPlayerOptionAcrossAllSubcommands() {
    assertThat(tracked.getCommandName()).isEqualTo("stats");
    assertThat(tracked.getOptionName()).isEqualTo("player");
    assertThat(tracked.getSubcommandName()).isNull();
  }

  @Test
  void trackedPlayer_offersTheFocusGroup() {
    List<Command.Choice> choices = tracked.getChoices("", FakeCommandContext.builder().build());

    assertThat(choices)
        .extracting(Command.Choice::getAsString)
        .containsExactlyInAnyOrder("Пастух лолей", "Tigress");
  }

  @Test
  void trackedPlayer_isListValuedSoStatsCanFanOut() {
    List<Command.Choice> choices =
        tracked.getChoices("Tigress, Пастух", FakeCommandContext.builder().build());

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("Tigress,Пастух лолей");
  }

  @Test
  void trackedPlayer_matchesCyrillicNamesCaseInsensitively() {
    List<Command.Choice> choices =
        tracked.getChoices("пастух", FakeCommandContext.builder().build());

    assertThat(choices).extracting(Command.Choice::getAsString).containsExactly("Пастух лолей");
  }

  @Test
  void trackedPlayer_looksUpTheParentChannelSoItWorksInsideThreads() {
    tracked.getChoices(
        "",
        FakeCommandContext.builder().channelId("thread-9").parentChannelId("channel-1").build());

    Mockito.verify(instanceConfigService).getByDiscordChannelId("channel-1");
  }

  @Test
  void trackedPlayer_unregisteredChannel_isEmpty() {
    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.empty());

    assertThat(tracked.getChoices("", FakeCommandContext.builder().build())).isEmpty();
  }

  @Test
  void trackedPlayer_serviceFailure_returnsEmptyRatherThanThrowing() {
    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenThrow(new IllegalStateException("db down"));

    assertThat(tracked.getChoices("", FakeCommandContext.builder().build())).isEmpty();
  }

  @Test
  void dbuffRemoveAndLink_reuseTheFocusGroupButAreSingleValued() {
    TrackedPlayerForDbuffAutocomplete remove = new TrackedPlayerForDbuffAutocomplete(tracked);
    LinkPlayerAutocomplete link = new LinkPlayerAutocomplete(tracked);

    assertThat(remove.getCommandName()).isEqualTo("dbuff");
    assertThat(remove.getSubcommandName()).isEqualTo("remove");
    assertThat(link.getSubcommandName()).isEqualTo("link");

    // Single-valued: a comma means "no match", so a list cannot be built here.
    assertThat(remove.getChoices("Tigress, Пастух", FakeCommandContext.builder().build()))
        .isEmpty();
    assertThat(remove.getChoices("Tigress", FakeCommandContext.builder().build())).hasSize(1);
  }

  @Test
  void knownPlayer_offersPlayersSeenInProcessedMatches() {
    PlayerRepo playerRepo = Mockito.mock(PlayerRepo.class);
    Mockito.when(playerRepo.findAll(Mockito.any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    PlayerDomain.builder().id(1L).name("Dendi").build(),
                    PlayerDomain.builder().id(2L).name("Puppey").build())));

    KnownPlayerAutocomplete provider = new KnownPlayerAutocomplete(playerRepo);
    List<Command.Choice> choices = provider.getChoices("den", FakeCommandContext.builder().build());

    assertThat(provider.getCommandName()).isEqualTo("scout");
    assertThat(provider.getOptionName()).isEqualTo("name");
    assertThat(choices).extracting(Command.Choice::getAsString).containsExactly("Dendi");
  }

  @Test
  void openDota_shortQueriesAreNotSearchedAtAll() throws Exception {
    SearchApi searchApi = Mockito.mock(SearchApi.class);
    OpenDotaPlayerAutocomplete provider =
        new OpenDotaPlayerAutocomplete(searchApi, RateLimiter.create(100.0));

    assertThat(provider.getChoices("de", FakeCommandContext.builder().build())).isEmpty();
    Mockito.verify(searchApi, Mockito.never()).getSearch(Mockito.anyString());
  }

  @Test
  void openDota_showsNameAndIdButSubmitsTheId() throws Exception {
    SearchApi searchApi = Mockito.mock(SearchApi.class);
    Mockito.when(searchApi.getSearch("dendi"))
        .thenReturn(List.of(searchResult(70388657L, "Dendi")));

    List<Command.Choice> choices =
        new OpenDotaPlayerAutocomplete(searchApi, RateLimiter.create(100.0))
            .getChoices("dendi", FakeCommandContext.builder().build());

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getName()).isEqualTo("Dendi (70388657)");
    assertThat(choices.get(0).getAsString()).isEqualTo("70388657");
  }

  @Test
  void openDota_cachesSoRepeatedKeystrokesDoNotReSearch() throws Exception {
    SearchApi searchApi = Mockito.mock(SearchApi.class);
    Mockito.when(searchApi.getSearch("dendi"))
        .thenReturn(List.of(searchResult(70388657L, "Dendi")));

    OpenDotaPlayerAutocomplete provider =
        new OpenDotaPlayerAutocomplete(searchApi, RateLimiter.create(100.0));
    provider.getChoices("dendi", FakeCommandContext.builder().build());
    provider.getChoices("dendi", FakeCommandContext.builder().build());

    Mockito.verify(searchApi, Mockito.times(1)).getSearch("dendi");
  }

  @Test
  void openDota_whenTheRateBudgetIsSpent_returnsEmptyWithoutCalling() throws Exception {
    SearchApi searchApi = Mockito.mock(SearchApi.class);
    // A limiter with an exhausted budget: the first tryAcquire consumes the only permit.
    RateLimiter spent = RateLimiter.create(0.0001);
    spent.tryAcquire();

    List<Command.Choice> choices =
        new OpenDotaPlayerAutocomplete(searchApi, spent)
            .getChoices("dendi", FakeCommandContext.builder().build());

    assertThat(choices).isEmpty();
    Mockito.verify(searchApi, Mockito.never()).getSearch(Mockito.anyString());
  }

  @Test
  void openDota_apiFailure_returnsEmptyRatherThanThrowing() throws Exception {
    SearchApi searchApi = Mockito.mock(SearchApi.class);
    Mockito.when(searchApi.getSearch("dendi")).thenThrow(new IllegalStateException("502"));

    assertThat(
            new OpenDotaPlayerAutocomplete(searchApi, RateLimiter.create(100.0))
                .getChoices("dendi", FakeCommandContext.builder().build()))
        .isEmpty();
  }

  @Test
  void openDota_resultsWithoutAnAccountIdAreSkipped() throws Exception {
    SearchApi searchApi = Mockito.mock(SearchApi.class);
    Mockito.when(searchApi.getSearch("dendi")).thenReturn(List.of(searchResult(null, "Ghost")));

    assertThat(
            new OpenDotaPlayerAutocomplete(searchApi, RateLimiter.create(100.0))
                .getChoices("dendi", FakeCommandContext.builder().build()))
        .isEmpty();
  }

  /**
   * General to {@code /dbuff} rather than one subcommand: {@code register} and {@code add} both
   * name untracked players. {@code remove} and {@code link} override it by being more specific.
   */
  @Test
  void openDota_servesDbuffPlayerOptionsWithoutTheirOwnProvider() {
    OpenDotaPlayerAutocomplete provider =
        new OpenDotaPlayerAutocomplete(Mockito.mock(SearchApi.class), RateLimiter.create(100.0));

    assertThat(provider.getCommandName()).isEqualTo("dbuff");
    assertThat(provider.getOptionName()).isEqualTo("player");
    assertThat(provider.getSubcommandName()).isNull();
  }

  private static SearchResponse searchResult(Long accountId, String personaname) {
    SearchResponse response = new SearchResponse();
    response.setAccountId(accountId);
    response.setPersonaname(personaname);
    return response;
  }
}
