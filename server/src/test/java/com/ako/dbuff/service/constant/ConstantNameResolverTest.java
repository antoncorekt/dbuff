package com.ako.dbuff.service.constant;

import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantNameResolverTest {

  private ConstantsManagers constantsManagers;
  private ConstantNameResolver resolver;

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
                "5002", AbilityIdsConstant.builder().id(5002L).name("invoker_wex").build()));

    resolver = new ConstantNameResolver(constantsManagers);
  }

  @Test
  void resolveItems_matchesShortNameAndDisplayName() {
    NameResolution result = resolver.resolveItems(Set.of("blink", "Black King Bar"));

    assertThat(result.resolvedIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThat(result.unresolvedNames()).isEmpty();
    assertThat(result.hasUnresolved()).isFalse();
  }

  @Test
  void resolveItems_isCaseInsensitive() {
    NameResolution result = resolver.resolveItems(Set.of("BLINK", "black king bar"));

    assertThat(result.resolvedIds()).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void resolveItems_reportsUnknownNamesInsteadOfDroppingThem() {
    NameResolution result = resolver.resolveItems(Set.of("blink", "Sheepstick Of Doom"));

    assertThat(result.resolvedIds()).containsExactly(1L);
    assertThat(result.unresolvedNames()).containsExactly("Sheepstick Of Doom");
    assertThat(result.hasUnresolved()).isTrue();
  }

  @Test
  void resolveItems_allUnknown_reportsThemAndResolvesNothing() {
    NameResolution result = resolver.resolveItems(Set.of("nonsense", "garbage"));

    assertThat(result.resolvedIds()).isEmpty();
    assertThat(result.unresolvedNames()).containsExactlyInAnyOrder("nonsense", "garbage");
    assertThat(result.hasUnresolved()).isTrue();
  }

  @Test
  void resolveItems_nullOrEmptyInput_isEmptyWithNoComplaints() {
    assertThat(resolver.resolveItems(null).resolvedIds()).isEmpty();
    assertThat(resolver.resolveItems(null).hasUnresolved()).isFalse();
    assertThat(resolver.resolveItems(Set.of()).resolvedIds()).isEmpty();
    assertThat(resolver.resolveItems(Set.of()).hasUnresolved()).isFalse();
  }

  @Test
  void resolveHeroes_matchesInternalAndLocalizedName() {
    assertThat(resolver.resolveHeroes(Set.of("Invoker")).resolvedIds()).containsExactly(74L);
    assertThat(resolver.resolveHeroes(Set.of("npc_dota_hero_antimage")).resolvedIds())
        .containsExactly(1L);
  }

  @Test
  void resolveHeroes_unknownHero_isReported() {
    NameResolution result = resolver.resolveHeroes(Set.of("Not A Hero"));

    assertThat(result.resolvedIds()).isEmpty();
    assertThat(result.unresolvedNames()).containsExactly("Not A Hero");
  }

  @Test
  void resolveAbilities_matchesInternalName() {
    NameResolution result = resolver.resolveAbilities(Set.of("invoker_quas", "INVOKER_WEX"));

    assertThat(result.resolvedIds()).containsExactlyInAnyOrder(5001L, 5002L);
    assertThat(result.unresolvedNames()).isEmpty();
  }

  @Test
  void resolveAbilities_unknownAbility_isReported() {
    NameResolution result = resolver.resolveAbilities(Set.of("invoker_quas", "made_up_spell"));

    assertThat(result.resolvedIds()).containsExactly(5001L);
    assertThat(result.unresolvedNames()).containsExactly("made_up_spell");
  }

  @Test
  void suggestItem_returnsClosestNameByEditDistance() {
    assertThat(resolver.suggestItem("blnk")).contains("Blink Dagger");
    assertThat(resolver.suggestItem("Black King Bra")).contains("Black King Bar");
  }

  @Test
  void suggestItem_whollyUnrelatedInput_returnsEmpty() {
    assertThat(resolver.suggestItem("zzzzzzzzzzzzzzzz")).isEmpty();
  }

  @Test
  void suggestHero_returnsClosestLocalizedName() {
    assertThat(resolver.suggestHero("Invokr")).contains("Invoker");
  }

  @Test
  void suggestAbility_returnsClosestInternalName() {
    assertThat(resolver.suggestAbility("invoker_qua")).contains("invoker_quas");
  }
}
