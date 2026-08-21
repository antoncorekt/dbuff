package com.ako.dbuff.service.constant;

import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class GameModeResolverTest {

  private static final Long ABILITY_DRAFT_ID = 18L;
  private static final Long ALL_DRAFT_ID = 22L;
  private static final Long ALL_PICK_ID = 1L;

  private GameModeResolver resolver;

  @BeforeEach
  void setUp() {
    ConstantsManagers constantsManagers = Mockito.mock(ConstantsManagers.class);
    Mockito.when(constantsManagers.getMatchTypeConstantMap()).thenReturn(dictionary());
    resolver = new GameModeResolver(constantsManagers);
  }

  /** Shaped like the OpenDota constant map: keyed by ID, values carrying the internal name. */
  private static Map<String, MatchTypeConstant> dictionary() {
    Map<String, MatchTypeConstant> modes = new LinkedHashMap<>();
    modes.put("1", mode("1", "game_mode_all_pick"));
    modes.put("18", mode("18", "game_mode_ability_draft"));
    modes.put("22", mode("22", "game_mode_all_draft"));
    return modes;
  }

  private static MatchTypeConstant mode(String id, String name) {
    return MatchTypeConstant.builder().id(id).name(name).build();
  }

  // ------------------------------------------------------------------ defaults

  @Test
  void noModeGiven_defaultsToAbilityDraft() {
    GameModeSelection selection = resolver.resolveOrDefault(Set.of());

    assertThat(selection.ids()).containsExactly(ABILITY_DRAFT_ID);
    assertThat(selection.label()).isEqualTo("Ability Draft");
    assertThat(selection.isAllModes()).isFalse();
  }

  @Test
  void nullTokens_defaultToAbilityDraft() {
    assertThat(resolver.resolveOrDefault(null).ids()).containsExactly(ABILITY_DRAFT_ID);
  }

  /**
   * {@code resolve} must NOT apply the command layer's default, or the REST API would silently
   * answer a narrower question than the one it was sent.
   */
  @Test
  void resolveWithoutDefault_treatsNothingGivenAsEveryMode() {
    assertThat(resolver.resolve(Set.of()).isAllModes()).isTrue();
    assertThat(resolver.resolve(null).idsOrNullIfEmpty()).isNull();
  }

  // ------------------------------------------------------------------- aliases

  /**
   * The mode the Dota client calls "All Pick" is {@code game_mode_all_draft} in the constants, so
   * the alias must not resolve to {@code game_mode_all_pick} — that is the unranked legacy mode and
   * would silently report the wrong games.
   */
  @Test
  void allPickAlias_resolvesToAllDraftNotTheLegacyAllPick() {
    GameModeSelection selection = resolver.resolveOrDefault(Set.of("all_pick"));

    assertThat(selection.ids()).containsExactly(ALL_DRAFT_ID);
    assertThat(selection.ids()).doesNotContain(ALL_PICK_ID);
  }

  @Test
  void abilityDraftAlias_resolvesToAbilityDraft() {
    assertThat(resolver.resolveOrDefault(Set.of("ability_draft")).ids())
        .containsExactly(ABILITY_DRAFT_ID);
  }

  @Test
  void all_clearsTheFilterEntirely() {
    GameModeSelection selection = resolver.resolveOrDefault(Set.of("all"));

    assertThat(selection.isAllModes()).isTrue();
    assertThat(selection.idsOrNullIfEmpty()).isNull();
    assertThat(selection.label()).isEqualTo("All modes");
  }

  /** "All" among named modes is the widest request in the list, so it wins. */
  @Test
  void allAlongsideANamedMode_stillMeansEveryMode() {
    assertThat(resolver.resolveOrDefault(Set.of("ability_draft", "all")).isAllModes()).isTrue();
  }

  // ------------------------------------------------------------- spelling forms

  @Test
  void acceptsAliasesConstantNamesDisplayNamesAndRawIds() {
    for (String spelling :
        Set.of(
            "ability_draft", "game_mode_ability_draft", "Ability Draft", "ABILITY DRAFT", "18")) {
      assertThat(resolver.resolveOrDefault(Set.of(spelling)).ids())
          .as("spelling '%s'", spelling)
          .containsExactly(ABILITY_DRAFT_ID);
    }
  }

  @Test
  void acceptsHyphensAndSurroundingWhitespace() {
    assertThat(resolver.resolveOrDefault(Set.of("  ability-draft ")).ids())
        .containsExactly(ABILITY_DRAFT_ID);
  }

  // ---------------------------------------------------------------------- lists

  @Test
  void resolvesAListIntoEveryMatchingId() {
    GameModeSelection selection = resolver.resolveOrDefault(Set.of("ability_draft", "all_pick"));

    assertThat(selection.ids()).containsExactlyInAnyOrder(ABILITY_DRAFT_ID, ALL_DRAFT_ID);
    assertThat(selection.label()).contains("Ability Draft").contains("All Draft");
    assertThat(selection.hasUnresolved()).isFalse();
  }

  // ----------------------------------------------------------------- unresolved

  /**
   * An empty ID set means "no filter" downstream, so a dropped typo would widen a mode-scoped
   * question into an every-mode answer with nothing to signal it.
   */
  @Test
  void unknownMode_isReportedRatherThanDropped() {
    GameModeSelection selection = resolver.resolveOrDefault(Set.of("abilty_draft"));

    assertThat(selection.hasUnresolved()).isTrue();
    assertThat(selection.unresolvedNames()).containsExactly("abilty_draft");
    assertThat(selection.isAllModes()).isFalse();
  }

  @Test
  void partiallyUnknownList_keepsTheGoodOnesAndReportsTheBad() {
    GameModeSelection selection = resolver.resolveOrDefault(Set.of("ability_draft", "nonsense"));

    assertThat(selection.ids()).containsExactly(ABILITY_DRAFT_ID);
    assertThat(selection.unresolvedNames()).containsExactly("nonsense");
  }

  @Test
  void suggestsTheNearestModeForATypo() {
    assertThat(resolver.suggest("abilty draft")).contains("Ability Draft");
  }

  @Test
  void suggestsNothingForInputNowhereNearAMode() {
    assertThat(resolver.suggest("qwertyuiopasdfgh")).isEmpty();
  }

  // ------------------------------------------------------------- picker choices

  @Test
  void pickerOffersTheTwoAliasesAndAllFirst() {
    Map<String, String> choices = resolver.displayToSubmittedValue();

    assertThat(choices).containsEntry("Ability Draft", "ability_draft");
    assertThat(choices).containsEntry("All Pick", "all_pick");
    assertThat(choices).containsEntry("All modes", "all");
    assertThat(choices.keySet()).startsWith("Ability Draft", "All Pick", "All modes");
  }

  /** Everything the instance can be configured to track must also be filterable. */
  @Test
  void pickerAlsoOffersEveryModeFromTheDictionary() {
    assertThat(resolver.displayToSubmittedValue().keySet()).contains("All Draft");
  }

  @Test
  void everyPickerValueResolvesBack() {
    resolver
        .displayToSubmittedValue()
        .forEach(
            (display, value) ->
                assertThat(resolver.resolveOrDefault(Set.of(value)).hasUnresolved())
                    .as("picker value '%s' for '%s'", value, display)
                    .isFalse());
  }
}
