package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.repo.AbilityRankingRepository;
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

class AbilityRankingServiceTest {

  private static final Long PLAYER_ID = 123L;

  private AbilityRankingRepository repository;
  private ConstantNameResolver resolver;
  private GameModeResolver gameModeResolver;
  private AbilityRankingService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(AbilityRankingRepository.class);
    resolver = Mockito.mock(ConstantNameResolver.class);
    gameModeResolver = Mockito.mock(GameModeResolver.class);
    service = new AbilityRankingService(repository, resolver, gameModeResolver);

    Mockito.when(
            repository.findAbilityRankingsByPlayer(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(List.of());
    Mockito.when(resolver.resolveAbilities(null)).thenReturn(NameResolution.empty());
    Mockito.when(resolver.resolveItems(null)).thenReturn(NameResolution.empty());
    Mockito.when(resolver.resolveHeroes(null)).thenReturn(NameResolution.empty());
    Mockito.when(gameModeResolver.resolve(Mockito.any())).thenReturn(GameModeSelection.allModes());
  }

  @Test
  void unknownAbilityName_throwsInsteadOfSilentlyWideningTheQuery() {
    Mockito.when(resolver.resolveAbilities(Set.of("garbage")))
        .thenReturn(new NameResolution(Set.of(), Set.of("garbage")));

    assertThatThrownBy(
            () ->
                service.getAbilityRankings(
                    PLAYER_ID, null, null, Set.of("garbage"), null, null, null, null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("garbage");

    Mockito.verify(repository, Mockito.never())
        .findAbilityRankingsByPlayer(
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
  void partiallyUnknownAbilityNames_throwsNamingOnlyTheBadOne() {
    Mockito.when(resolver.resolveAbilities(Set.of("Quas", "garbage")))
        .thenReturn(new NameResolution(Set.of(5001L), Set.of("garbage")));

    assertThatThrownBy(
            () ->
                service.getAbilityRankings(
                    PLAYER_ID, null, null, Set.of("Quas", "garbage"), null, null, null, null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("garbage")
        .hasMessageNotContaining("Quas");
  }

  @Test
  void knownAbilityNames_passResolvedIdsToRepository() {
    Mockito.when(resolver.resolveAbilities(Set.of("Quas")))
        .thenReturn(new NameResolution(Set.of(5001L), Set.of()));

    service.getAbilityRankings(PLAYER_ID, null, null, Set.of("Quas"), null, null, null, null);

    assertThat(capturedArgument(3)).isEqualTo(Set.of(5001L));
  }

  @Test
  void noAbilityFilter_passesNullSoRepositoryReturnsTopN() {
    service.getAbilityRankings(PLAYER_ID, null, null, null, null, null, null, null);

    assertThat(capturedArgument(3)).isNull();
  }

  @Test
  void unknownHeroName_throws() {
    Mockito.when(resolver.resolveHeroes(Set.of("Not A Hero")))
        .thenReturn(new NameResolution(Set.of(), Set.of("Not A Hero")));

    assertThatThrownBy(
            () ->
                service.getAbilityRankings(
                    PLAYER_ID, null, null, null, null, Set.of("Not A Hero"), null, null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("Not A Hero");
  }

  @Test
  void heroFilter_isForwardedToRepository() {
    Mockito.when(resolver.resolveHeroes(Set.of("Invoker")))
        .thenReturn(new NameResolution(Set.of(74L), Set.of()));

    service.getAbilityRankings(PLAYER_ID, null, null, null, null, Set.of("Invoker"), null, null);

    assertThat(capturedArgument(5)).isEqualTo(Set.of(74L));
  }

  @Test
  void endDateDefaultsToToday() {
    service.getAbilityRankings(PLAYER_ID, null, null, null, null, null, null, null);

    assertThat(capturedArgument(2)).isEqualTo(LocalDate.now());
  }

  @Test
  void limitDefaultsToTen() {
    service.getAbilityRankings(PLAYER_ID, null, null, null, null, null, null, null);

    assertThat(capturedArgument(7)).isEqualTo(10);
  }

  @Test
  void unknownGameMode_throwsInsteadOfSilentlyWideningToEveryMode() {
    Mockito.when(gameModeResolver.resolve(Set.of("abilty_draft")))
        .thenReturn(new GameModeSelection(Set.of(), Set.of(), Set.of("abilty_draft"), "All modes"));

    assertThatThrownBy(
            () ->
                service.getAbilityRankings(
                    PLAYER_ID, null, null, null, null, null, Set.of("abilty_draft"), null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("abilty_draft");

    Mockito.verifyNoInteractions(repository);
  }

  @Test
  void gameModeFilter_isForwardedToRepository() {
    Mockito.when(gameModeResolver.resolve(Set.of("ability_draft")))
        .thenReturn(
            new GameModeSelection(
                Set.of("game_mode_ability_draft"), Set.of(18L), Set.of(), "Ability Draft"));

    service.getAbilityRankings(
        PLAYER_ID, null, null, null, null, null, Set.of("ability_draft"), null);

    assertThat(capturedArgument(6)).isEqualTo(Set.of(18L));
  }

  @Test
  void noGameModeFilter_passesNullSoEveryModeIsIncluded() {
    service.getAbilityRankings(PLAYER_ID, null, null, null, null, null, null, null);

    assertThat(capturedArgument(6)).isNull();
  }

  @Test
  void comboStatistics_unknownAbilityName_throwsBeforeQuerying() {
    Mockito.when(resolver.resolveAbilities(Set.of("garbage")))
        .thenReturn(new NameResolution(Set.of(), Set.of("garbage")));

    assertThatThrownBy(
            () ->
                service.getAbilityComboStatistics(
                    PLAYER_ID, null, null, Set.of("garbage"), null, null, null))
        .isInstanceOf(UnknownConstantNameException.class);

    Mockito.verify(repository, Mockito.never())
        .findAbilityComboStatistics(
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any());
  }

  @Test
  void comboStatistics_forwardsResolvedAbilityAndHeroIds() {
    Mockito.when(resolver.resolveAbilities(Set.of("Quas", "Wex")))
        .thenReturn(new NameResolution(Set.of(5001L, 5002L), Set.of()));
    Mockito.when(resolver.resolveHeroes(Set.of("Invoker")))
        .thenReturn(new NameResolution(Set.of(74L), Set.of()));

    service.getAbilityComboStatistics(
        PLAYER_ID, null, null, Set.of("Quas", "Wex"), null, Set.of("Invoker"), null);

    Mockito.verify(repository)
        .findAbilityComboStatistics(
            PLAYER_ID, Set.of(5001L, 5002L), null, Set.of(74L), null, null, LocalDate.now());
  }

  @Test
  void comboStatistics_forwardsItemIdsSoTheConjunctionCoversBoth() {
    Mockito.when(resolver.resolveAbilities(Set.of("Quas")))
        .thenReturn(new NameResolution(Set.of(5001L), Set.of()));
    Mockito.when(resolver.resolveItems(Set.of("blink")))
        .thenReturn(new NameResolution(Set.of(1L), Set.of()));

    service.getAbilityComboStatistics(
        PLAYER_ID, null, null, Set.of("Quas"), Set.of("blink"), null, null);

    Mockito.verify(repository)
        .findAbilityComboStatistics(
            PLAYER_ID, Set.of(5001L), Set.of(1L), null, null, null, LocalDate.now());
  }

  @Test
  void comboStatistics_unknownItemName_throwsBeforeQuerying() {
    Mockito.when(resolver.resolveAbilities(Set.of("Quas")))
        .thenReturn(new NameResolution(Set.of(5001L), Set.of()));
    Mockito.when(resolver.resolveItems(Set.of("blnk")))
        .thenReturn(new NameResolution(Set.of(), Set.of("blnk")));

    assertThatThrownBy(
            () ->
                service.getAbilityComboStatistics(
                    PLAYER_ID, null, null, Set.of("Quas"), Set.of("blnk"), null, null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("blnk");

    Mockito.verifyNoInteractions(repository);
  }

  /**
   * Returns the argument at {@code index} from the single repository call.
   *
   * <p>Positional rather than named because the method takes eight same-typed nullable parameters;
   * ArgumentCaptor per parameter would be far more code for no more clarity. Index order is
   * (playerId, startDate, endDate, abilityIds, excludedAbilities, heroIds, gameModeIds, limit).
   */
  private Object capturedArgument(int index) {
    return Mockito.mockingDetails(repository).getInvocations().stream()
        .findFirst()
        .orElseThrow(() -> new AssertionError("Repository was never called"))
        .getArgument(index);
  }
}
