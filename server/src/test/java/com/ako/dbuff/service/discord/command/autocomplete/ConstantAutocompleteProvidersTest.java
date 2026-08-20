package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.HeroesAbilityConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the four constant-backed autocomplete providers. */
class ConstantAutocompleteProvidersTest {

  private ConstantsManagers constantsManagers;

  @BeforeEach
  void setUp() {
    constantsManagers = Mockito.mock(ConstantsManagers.class);

    Mockito.when(constantsManagers.getItemConstantMap())
        .thenReturn(
            Map.of(
                "blink", ItemConstant.builder().id(1L).dname("Blink Dagger").build(),
                "black_king_bar", ItemConstant.builder().id(2L).dname("Black King Bar").build()));

    Mockito.when(constantsManagers.getHeroConstantMap())
        .thenReturn(
            Map.of(
                "1", new HeroConstant("1", "npc_dota_hero_antimage", "Anti-Mage"),
                "74", new HeroConstant("74", "npc_dota_hero_invoker", "Invoker")));

    Mockito.when(constantsManagers.getAbilityConstantMap())
        .thenReturn(
            Map.of(
                "5001", AbilityIdsConstant.builder().id(5001L).name("invoker_quas").build(),
                "5002", AbilityIdsConstant.builder().id(5002L).name("invoker_wex").build(),
                "5003", AbilityIdsConstant.builder().id(5003L).name("antimage_blink").build()));

    Mockito.when(constantsManagers.getHeroAbilitiesMap())
        .thenReturn(
            Map.of(
                "npc_dota_hero_invoker",
                    HeroesAbilityConstant.builder()
                        .abilities(List.of("invoker_quas", "invoker_wex"))
                        .build(),
                "npc_dota_hero_antimage",
                    HeroesAbilityConstant.builder().abilities(List.of("antimage_blink")).build()));

    Mockito.when(constantsManagers.getMatchTypeConstantMap())
        .thenReturn(
            Map.of(
                "22", MatchTypeConstant.builder().id("22").name("game_mode_all_pick").build(),
                "23", MatchTypeConstant.builder().id("23").name("game_mode_turbo").build()));
  }

  @Test
  void itemAutocomplete_servesTheItemsOptionOfStats() {
    ItemAutocomplete provider = new ItemAutocomplete(constantsManagers);

    assertThat(provider.getOptionName()).isEqualTo("items");
    assertThat(provider.getCommandName()).isEqualTo("stats");
  }

  @Test
  void itemAutocomplete_offersDisplayNamesAndSubmitsShortNames() {
    List<Command.Choice> choices =
        new ItemAutocomplete(constantsManagers)
            .getChoices("blink", FakeCommandContext.builder().build());

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getName()).isEqualTo("Blink Dagger");
    assertThat(choices.get(0).getAsString()).isEqualTo("blink");
  }

  @Test
  void itemAutocomplete_accumulatesAcrossCommas() {
    List<Command.Choice> choices =
        new ItemAutocomplete(constantsManagers)
            .getChoices("blink, black", FakeCommandContext.builder().build());

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("blink,black_king_bar");
  }

  @Test
  void itemAutocomplete_itemsWithoutADisplayNameAreSkipped() {
    Mockito.when(constantsManagers.getItemConstantMap())
        .thenReturn(Map.of("weird", ItemConstant.builder().id(9L).dname(null).build()));

    assertThat(
            new ItemAutocomplete(constantsManagers)
                .getChoices("weird", FakeCommandContext.builder().build()))
        .isEmpty();
  }

  @Test
  void itemAutocomplete_constantLookupFailure_returnsEmptyRatherThanThrowing() {
    Mockito.when(constantsManagers.getItemConstantMap())
        .thenThrow(new IllegalStateException("cache cold"));

    assertThat(
            new ItemAutocomplete(constantsManagers)
                .getChoices("blink", FakeCommandContext.builder().build()))
        .isEmpty();
  }

  @Test
  void heroAutocomplete_isSingleValuedAndSubmitsLocalizedName() {
    HeroAutocomplete provider = new HeroAutocomplete(constantsManagers);

    List<Command.Choice> choices =
        provider.getChoices("invo", FakeCommandContext.builder().build());

    assertThat(provider.getOptionName()).isEqualTo("hero");
    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("Invoker");
  }

  @Test
  void heroAutocomplete_rejectsCommaSeparatedInput() {
    assertThat(
            new HeroAutocomplete(constantsManagers)
                .getChoices("Invoker, Anti-Mage", FakeCommandContext.builder().build()))
        .isEmpty();
  }

  @Test
  void abilityAutocomplete_withoutAHero_offersEveryAbility() {
    List<Command.Choice> choices =
        new AbilityAutocomplete(constantsManagers)
            .getChoices("", FakeCommandContext.builder().build());

    assertThat(choices).hasSize(3);
  }

  @Test
  void abilityAutocomplete_narrowsToTheChosenHerosAbilities() {
    List<Command.Choice> choices =
        new AbilityAutocomplete(constantsManagers)
            .getChoices("", FakeCommandContext.builder().option("hero", "Invoker").build());

    assertThat(choices)
        .extracting(Command.Choice::getAsString)
        .containsExactlyInAnyOrder("invoker_quas", "invoker_wex");
  }

  @Test
  void abilityAutocomplete_acceptsTheInternalHeroNameToo() {
    List<Command.Choice> choices =
        new AbilityAutocomplete(constantsManagers)
            .getChoices(
                "", FakeCommandContext.builder().option("hero", "npc_dota_hero_antimage").build());

    assertThat(choices).extracting(Command.Choice::getAsString).containsExactly("antimage_blink");
  }

  @Test
  void abilityAutocomplete_unresolvableHero_offersEverythingRatherThanNothing() {
    // Null narrowing, never an empty set: an empty picker reads as broken.
    List<Command.Choice> choices =
        new AbilityAutocomplete(constantsManagers)
            .getChoices("", FakeCommandContext.builder().option("hero", "Not A Hero").build());

    assertThat(choices).hasSize(3);
  }

  @Test
  void abilityAutocomplete_heroWithNoRecordedAbilities_offersEverything() {
    Mockito.when(constantsManagers.getHeroAbilitiesMap())
        .thenReturn(
            Map.of(
                "npc_dota_hero_invoker",
                HeroesAbilityConstant.builder().abilities(List.of()).build()));

    List<Command.Choice> choices =
        new AbilityAutocomplete(constantsManagers)
            .getChoices("", FakeCommandContext.builder().option("hero", "Invoker").build());

    assertThat(choices).hasSize(3);
  }

  @Test
  void abilityAutocomplete_accumulatesWithinTheNarrowedSet() {
    List<Command.Choice> choices =
        new AbilityAutocomplete(constantsManagers)
            .getChoices(
                "invoker_quas, wex",
                FakeCommandContext.builder().option("hero", "Invoker").build());

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("invoker_quas,invoker_wex");
  }

  @Test
  void gameModeAutocomplete_showsPrettyNamesAndSubmitsNumericIds() {
    GameModeAutocomplete provider = new GameModeAutocomplete(constantsManagers);

    List<Command.Choice> choices = provider.getChoices("all", FakeCommandContext.builder().build());

    assertThat(provider.getOptionName()).isEqualTo("mode");
    assertThat(provider.getCommandName()).isEqualTo("dbuff");
    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getName()).isEqualTo("All Pick");
    assertThat(choices.get(0).getAsString()).isEqualTo("22");
  }

  @Test
  void gameModeAutocomplete_matchesOnThePrettyName() {
    List<Command.Choice> choices =
        new GameModeAutocomplete(constantsManagers)
            .getChoices("Turbo", FakeCommandContext.builder().build());

    assertThat(choices).hasSize(1);
    assertThat(choices.get(0).getAsString()).isEqualTo("23");
  }
}
