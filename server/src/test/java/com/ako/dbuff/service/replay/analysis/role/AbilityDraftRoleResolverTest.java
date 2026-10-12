package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbilityDraftRoleResolverTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final Map<String, HeroConstant> HEROES =
      load("constants_profile_heroes.json", new TypeReference<>() {});
  private static final Map<String, AbilityConstant> ABILITIES =
      load("constants_profile_abilities.json", new TypeReference<>() {});

  private static final Map<Long, List<String>> KITS =
      Map.of(
          1L,
          List.of(
              "antimage_mana_break",
              "antimage_blink",
              "antimage_counterspell",
              "antimage_mana_void"),
          18L,
          List.of("sven_storm_bolt", "sven_great_cleave", "sven_warcry", "sven_gods_strength"),
          2L,
          List.of(
              "axe_berserkers_call", "axe_battle_hunger", "axe_counter_helix", "axe_culling_blade"),
          5L,
          List.of(
              "crystal_maiden_crystal_nova",
              "crystal_maiden_frostbite",
              "crystal_maiden_brilliance_aura",
              "crystal_maiden_freezing_field"),
          57L,
          List.of(
              "omniknight_purification",
              "omniknight_martyr",
              "omniknight_hammer_of_purity",
              "omniknight_guardian_angel"));
  private static final List<Long> HEROES_BY_WORTH = List.of(1L, 18L, 2L, 5L, 57L);

  private final HeroKits kits =
      new HeroKits() {
        @Override
        public Optional<HeroConstant> hero(long heroId) {
          return Optional.ofNullable(HEROES.get(String.valueOf(heroId)));
        }

        @Override
        public Optional<AbilityConstant> ability(String name) {
          return Optional.ofNullable(ABILITIES.get(name));
        }
      };
  private final RoleProfileProperties properties = new RoleProfileProperties();
  private final IntentStageProfiler intent =
      new IntentStageProfiler(
          kits, new IntentProfileCalculator(properties, RoleOverrides.NONE), RoleOverrides.NONE);
  private final ObservedProfiler observed =
      new ObservedProfiler(new ReplayAnalysisProperties(), properties);
  private final AbilityDraftRoleResolver resolver =
      new AbilityDraftRoleResolver(
          new StageProfilers(List.of(intent, observed)), new RoleFitter(properties));

  private static <T> T load(String resource, TypeReference<T> type) {
    try (InputStream in =
        AbilityDraftRoleResolverTest.class.getResourceAsStream("/opendota/" + resource)) {
      return MAPPER.readValue(in, type);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<DraftedAbility> drafted(long hero) {
    List<String> names = KITS.get(hero);
    List<DraftedAbility> result = new ArrayList<>();
    for (int i = 0; i < names.size(); i++) {
      result.add(new DraftedAbility(names.get(i), i == names.size() - 1));
    }
    return result;
  }

  private static List<RolePlayer> team(boolean withDraft) {
    List<RolePlayer> team = new ArrayList<>();
    for (int i = 0; i < HEROES_BY_WORTH.size(); i++) {
      long hero = HEROES_BY_WORTH.get(i);
      team.add(
          new RolePlayer(
              i,
              null,
              24000L - i * 4000L,
              null,
              List.of(),
              hero,
              withDraft ? drafted(hero) : List.of()));
    }
    return team;
  }

  private static Map<Long, Integer> roles(List<RoleAssignment> assignments) {
    Map<Long, Integer> roles = new HashMap<>();
    assignments.forEach(a -> roles.put(a.slot(), a.role()));
    return roles;
  }

  @Test
  void itServesAbilityDraftWithBothStagesAndTheDraftExtractor() {
    assertThat(resolver.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(resolver.supports(TestModes.ALL_PICK)).isFalse();
    assertThat(resolver.stages()).containsExactlyInAnyOrder(RoleStage.INTENT, RoleStage.FINAL);
    assertThat(resolver.requires()).containsExactly("ability-draft");
    assertThat(resolver.scoredAs(RoleStage.FINAL)).isEqualTo(RoleStage.FINAL);
  }

  @Test
  void theIntentGivesEachRoleOnceAndTheHealerKitTheHardSupport() {
    List<RoleAssignment> result = resolver.resolve(new RoleContext(RoleStage.INTENT, team(true)));

    assertThat(result).extracting(RoleAssignment::role).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    Map<Long, Integer> roles = roles(result);
    assertThat(roles.get(4L)).as("Omniknight, the healer").isEqualTo(5);
    assertThat(result)
        .allSatisfy(
            a -> {
              assertThat(a.source()).isEqualTo(PlayerRoleSource.DERIVED);
              assertThat(a.confidence()).isBetween(0.0, 1.0);
              assertThat(a.evidence()).startsWith("intent fit, best roles by score: ");
            });
  }

  @Test
  void theFinalFollowsTheNetWorthOfTheTeam() {
    List<RoleAssignment> result = resolver.resolve(new RoleContext(RoleStage.FINAL, team(true)));

    Map<Long, Integer> roles = roles(result);
    assertThat(roles.values()).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    assertThat(roles.get(0L)).as("richest").isLessThan(roles.get(4L));
    assertThat(roles.get(4L)).as("poorest").isGreaterThanOrEqualTo(4);
    assertThat(result).allSatisfy(a -> assertThat(a.evidence()).startsWith("final fit"));
  }

  @Test
  void theFinalNeedsNoDraftButTheIntentDoes() {
    assertThat(resolver.resolve(new RoleContext(RoleStage.FINAL, team(false)))).hasSize(5);

    List<RolePlayer> noKnowledge = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      noKnowledge.add(new RolePlayer(i, null, null, null, List.of()));
    }
    assertThat(resolver.resolve(new RoleContext(RoleStage.INTENT, noKnowledge))).isEmpty();
  }

  @Test
  void aFitterThatReturnsTooFewFitsGivesNoRolesInsteadOfAMisalignedOnes() {
    RoleFitter shortFitter = mock(RoleFitter.class);
    when(shortFitter.fit(anyList(), any())).thenReturn(List.of());
    AbilityDraftRoleResolver broken =
        new AbilityDraftRoleResolver(new StageProfilers(List.of(intent, observed)), shortFitter);

    assertThat(broken.resolve(new RoleContext(RoleStage.INTENT, team(true)))).isEmpty();
  }

  @Test
  void aTeamOfFourHasNoRoles() {
    List<RolePlayer> four = team(true).subList(0, 4);

    assertThat(resolver.resolve(new RoleContext(RoleStage.INTENT, four))).isEmpty();
    assertThat(resolver.resolve(new RoleContext(RoleStage.FINAL, four))).isEmpty();
  }
}
