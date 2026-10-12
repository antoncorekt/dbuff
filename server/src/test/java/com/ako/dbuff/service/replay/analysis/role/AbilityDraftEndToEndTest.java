package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisService;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.analysis.composition.CompositionProperties;
import com.ako.dbuff.service.replay.analysis.composition.DefaultCompositionRules;
import com.ako.dbuff.service.replay.analysis.composition.TeamCompositionAnalyzer;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.ako.dbuff.service.replay.report.CompositionSection;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.ako.dbuff.service.replay.report.ReplayReportProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An Ability Draft match through the analysis service: the roles analyzer stores INTENT and FINAL,
 * the composition analyzer a PICK and a FINAL verdict per team, and the composition section shows
 * what each tracked player was meant to play.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({
  PlayerRoleAnalyzer.class,
  AbilityDraftRoleResolver.class,
  AllPickRoleResolver.class,
  TeamCompositionAnalyzer.class,
  DefaultCompositionRules.class,
  CompositionProperties.class,
  ReplayAnalysisService.class,
  CompositionSection.class,
  ReplayReportProperties.class,
  StoredActivity.class,
  StoredMatchModes.class,
  GameModeRegistry.class,
  ReplayProcessingProperties.class,
  ReplayAnalysisProperties.class,
  AllPickProfile.class,
  AbilityDraftProfile.class,
  RoleProfileTestConfig.class,
  AbilityDraftEndToEndTest.Kits.class
})
class AbilityDraftEndToEndTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final Map<String, HeroConstant> HEROES =
      load("constants_profile_heroes.json", new TypeReference<>() {});
  private static final Map<String, AbilityConstant> ABILITIES =
      load("constants_profile_abilities.json", new TypeReference<>() {});

  private static final long MATCH = 51L;
  private static final long[] SLOTS = {0, 1, 2, 3, 4, 128, 129, 130, 131, 132};
  private static final long[] HEROES_OF_TEAM = {1, 18, 2, 5, 57};
  private static final List<List<String>> KITS =
      List.of(
          List.of(
              "antimage_mana_break",
              "antimage_blink",
              "antimage_counterspell",
              "antimage_mana_void"),
          List.of("sven_storm_bolt", "sven_great_cleave", "sven_warcry", "sven_gods_strength"),
          List.of(
              "axe_berserkers_call", "axe_battle_hunger", "axe_counter_helix", "axe_culling_blade"),
          List.of(
              "crystal_maiden_crystal_nova",
              "crystal_maiden_frostbite",
              "crystal_maiden_brilliance_aura",
              "crystal_maiden_freezing_field"),
          List.of(
              "omniknight_purification",
              "omniknight_martyr",
              "omniknight_hammer_of_purity",
              "omniknight_guardian_angel"));

  @TestConfiguration
  static class Kits {
    @Bean
    HeroKits heroKits() {
      return new HeroKits() {
        @Override
        public Optional<HeroConstant> hero(long heroId) {
          return Optional.ofNullable(HEROES.get(String.valueOf(heroId)));
        }

        @Override
        public Optional<AbilityConstant> ability(String name) {
          return Optional.ofNullable(ABILITIES.get(name));
        }
      };
    }
  }

  @Autowired private ReplayAnalysisService service;
  @Autowired private PlayerRoleRepo roleRepo;
  @Autowired private PlayerRoleProfileRepo profileRepo;
  @Autowired private TeamCompositionRepo compositionRepo;
  @Autowired private PlayerGameStatisticRepo statisticRepo;
  @Autowired private AbilityDraftPickRepo pickRepo;
  @Autowired private CompositionSection section;
  @Autowired private EntityManager entityManager;

  private static <T> T load(String resource, TypeReference<T> type) {
    try (InputStream in =
        AbilityDraftEndToEndTest.class.getResourceAsStream("/opendota/" + resource)) {
      return MAPPER.readValue(in, type);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void stored() {
    entityManager.persist(MatchDomain.builder().id(MATCH).gameModeId(18L).build());
    int order = 1;
    for (int i = 0; i < SLOTS.length; i++) {
      PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
      stat.setMatchId(MATCH);
      stat.setPlayerSlot(SLOTS[i]);
      stat.setHeroId(HEROES_OF_TEAM[i % 5]);
      stat.setNetWorth(30000L - (i % 5) * 5000L);
      statisticRepo.save(stat);
      List<String> kit = KITS.get(i % 5);
      for (int s = 0; s < kit.size(); s++) {
        pickRepo.save(pick(order++, SLOTS[i], kit.get(s), s == kit.size() - 1, s));
      }
    }
    entityManager.flush();
  }

  private static AbilityDraftPickDomain pick(
      int order, long slot, String name, boolean ultimate, int finalSlot) {
    return AbilityDraftPickDomain.builder()
        .matchId(MATCH)
        .pickOrder(order)
        .playerSlot(slot)
        .abilityName(name)
        .finalAbilitySlot(finalSlot)
        .ultimate(ultimate)
        .build();
  }

  @Test
  void anAbilityDraftMatchGetsIntentAndFinalRolesAndAPickAndAFinalVerdictPerTeam() {
    stored();

    List<String> failed = service.analyze(MATCH, TestModes.ABILITY_DRAFT, List.of());

    assertThat(failed).isEmpty();
    assertThat(roleRepo.findByMatchId(MATCH)).hasSize(10);
    List<PlayerRoleProfileDomain> profiles = profileRepo.findByMatchId(MATCH);
    assertThat(profiles.stream().filter(p -> p.getStage().equals("INTENT"))).hasSize(10);
    assertThat(profiles.stream().filter(p -> p.getStage().equals("FINAL"))).hasSize(10);
    assertThat(profiles.stream().filter(p -> p.getStage().equals("INTENT")))
        .allSatisfy(p -> assertThat(p.getEvidence()).startsWith("intent fit"));
    List<TeamCompositionDomain> compositions = compositionRepo.findByMatchId(MATCH);
    assertThat(compositions)
        .extracting(c -> c.getTeam() + " " + c.getStage())
        .containsExactlyInAnyOrder("RADIANT PICK", "RADIANT FINAL", "DIRE PICK", "DIRE FINAL");
    assertThat(compositions).allSatisfy(c -> assertThat(c.getSummary()).isNotBlank());
  }

  @Test
  void theCompositionSectionShowsTheIntentAndTheFinalRoleAndBothVerdicts() {
    stored();
    service.analyze(MATCH, TestModes.ABILITY_DRAFT, List.of());

    List<String> lines =
        section.lines(
            new ReplayReportInput(
                MATCH, null, Map.of(0L, "Anton").keySet(), Map.of(0L, "Anton"), 3));

    assertThat(lines.getFirst()).isEqualTo("**Roles and composition**");
    assertThat(lines.get(1)).startsWith("Anton was meant to play ").contains(" and played ");
    assertThat(lines).anyMatch(l -> l.startsWith("Radiant at the draft: "));
    assertThat(lines).anyMatch(l -> l.startsWith("Radiant in the game: "));
    assertThat(lines).noneMatch(l -> l.startsWith("Dire"));
  }
}
