package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.repo.ItemRankingRepository;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.GameModeResolver;
import com.ako.dbuff.service.constant.GameModeSelection;
import com.ako.dbuff.service.constant.NameResolution;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemRankingServiceTest {

  private static final Long PLAYER_ID = 123L;

  private ItemRankingRepository repository;
  private ConstantNameResolver resolver;
  private GameModeResolver gameModeResolver;
  private ItemRankingService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(ItemRankingRepository.class);
    resolver = Mockito.mock(ConstantNameResolver.class);
    gameModeResolver = Mockito.mock(GameModeResolver.class);
    service = new ItemRankingService(repository, resolver, gameModeResolver);

    Mockito.when(
            repository.findItemRankingsByPlayer(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(List.of());
    Mockito.when(resolver.resolveItems(null)).thenReturn(NameResolution.empty());
    Mockito.when(resolver.resolveHeroes(null)).thenReturn(NameResolution.empty());
    Mockito.when(gameModeResolver.resolve(Mockito.any())).thenReturn(GameModeSelection.allModes());
  }

  @Test
  void unknownItemName_throwsInsteadOfSilentlyWideningTheQuery() {
    Mockito.when(resolver.resolveItems(Set.of("garbage")))
        .thenReturn(new NameResolution(Set.of(), Set.of("garbage")));

    assertThatThrownBy(
            () ->
                service.getItemRankings(
                    PLAYER_ID, null, null, Set.of("garbage"), null, null, null, null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("garbage");

    Mockito.verify(repository, Mockito.never())
        .findItemRankingsByPlayer(
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any());
  }

  @Test
  void partiallyUnknownItemNames_throwsNamingOnlyTheBadOne() {
    Mockito.when(resolver.resolveItems(Set.of("blink", "garbage")))
        .thenReturn(new NameResolution(Set.of(1L), Set.of("garbage")));

    assertThatThrownBy(
            () ->
                service.getItemRankings(
                    PLAYER_ID, null, null, Set.of("blink", "garbage"), null, null, null, null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("garbage")
        .hasMessageNotContaining("blink");
  }

  @Test
  void knownItemNames_passResolvedIdsToRepository() {
    Mockito.when(resolver.resolveItems(Set.of("blink")))
        .thenReturn(new NameResolution(Set.of(1L), Set.of()));

    service.getItemRankings(PLAYER_ID, null, null, Set.of("blink"), null, null, null, null);

    assertThat(capturedArgument(3)).isEqualTo(Set.of(1L));
  }

  @Test
  void noItemFilter_passesNullSoRepositoryReturnsTopN() {
    service.getItemRankings(PLAYER_ID, null, null, null, null, null, null, null);

    assertThat(capturedArgument(3)).isNull();
  }

  @Test
  void unknownHeroName_throws() {
    Mockito.when(resolver.resolveHeroes(Set.of("Not A Hero")))
        .thenReturn(new NameResolution(Set.of(), Set.of("Not A Hero")));

    assertThatThrownBy(
            () ->
                service.getItemRankings(
                    PLAYER_ID, null, null, null, null, Set.of("Not A Hero"), null, null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("Not A Hero");
  }

  @Test
  void heroFilter_isForwardedToRepository() {
    Mockito.when(resolver.resolveHeroes(Set.of("Invoker")))
        .thenReturn(new NameResolution(Set.of(74L), Set.of()));

    service.getItemRankings(PLAYER_ID, null, null, null, null, Set.of("Invoker"), null, null);

    assertThat(capturedArgument(5)).isEqualTo(Set.of(74L));
  }

  @Test
  void endDateDefaultsToToday() {
    service.getItemRankings(PLAYER_ID, null, null, null, null, null, null, null);

    assertThat(capturedArgument(2)).isEqualTo(LocalDate.now());
  }

  @Test
  void limitDefaultsToTen() {
    service.getItemRankings(PLAYER_ID, null, null, null, null, null, null, null);

    assertThat(capturedArgument(7)).isEqualTo(10);
  }

  @Test
  void unknownGameMode_throwsInsteadOfSilentlyWideningToEveryMode() {
    Mockito.when(gameModeResolver.resolve(Set.of("all_pik")))
        .thenReturn(new GameModeSelection(Set.of(), Set.of(), Set.of("all_pik"), "All modes"));

    assertThatThrownBy(
            () ->
                service.getItemRankings(
                    PLAYER_ID, null, null, null, null, null, Set.of("all_pik"), null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("all_pik");

    Mockito.verifyNoInteractions(repository);
  }

  @Test
  void gameModeFilter_isForwardedToRepository() {
    Mockito.when(gameModeResolver.resolve(Set.of("ability_draft")))
        .thenReturn(
            new GameModeSelection(
                Set.of("game_mode_ability_draft"), Set.of(18L), Set.of(), "Ability Draft"));

    service.getItemRankings(PLAYER_ID, null, null, null, null, null, Set.of("ability_draft"), null);

    assertThat(capturedArgument(6)).isEqualTo(Set.of(18L));
  }

  @Test
  void noGameModeFilter_passesNullSoEveryModeIsIncluded() {
    service.getItemRankings(PLAYER_ID, null, null, null, null, null, null, null);

    assertThat(capturedArgument(6)).isNull();
  }

  /**
   * Returns the argument at {@code index} from the single repository call.
   *
   * <p>Positional rather than named because the method takes eight same-typed nullable parameters;
   * ArgumentCaptor per parameter would be far more code for no more clarity. Index order is
   * (playerId, startDate, endDate, itemIds, excludedItems, heroIds, gameModeIds, limit).
   */
  private Object capturedArgument(int index) {
    return Mockito.mockingDetails(repository).getInvocations().stream()
        .findFirst()
        .orElseThrow(() -> new AssertionError("Repository was never called"))
        .getArgument(index);
  }
}
