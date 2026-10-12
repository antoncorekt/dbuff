package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.service.constant.data.AbilityAttribute;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.mode.TestModes;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shows that a resolver for another mode, with intent and final stages, is a new bean and no edit
 * of the analyzer: the resolver here is built from the stage profilers and the fitter, as the
 * Ability Draft one will be.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({
  PlayerRoleAnalyzer.class,
  StoredActivity.class,
  StoredMatchModes.class,
  GameModeRegistry.class,
  ReplayProcessingProperties.class,
  AllPickProfile.class,
  AbilityDraftProfile.class,
  RoleProfileTestConfig.class,
  PlayerRoleStageAnalyzerTest.Config.class
})
class PlayerRoleStageAnalyzerTest {

  private static final long MATCH = 41L;
  private static final long[] SLOTS = {0, 1, 2, 3, 4, 128, 129, 130, 131, 132};
  private static final long STUNNER_HERO = 27;

  private static final AtomicBoolean FAIL_INTENT = new AtomicBoolean();

  @TestConfiguration
  static class Config {
    @Bean
    HeroKits heroKits() {
      return new HeroKits() {
        @Override
        public Optional<HeroConstant> hero(long heroId) {
          return Optional.of(HeroConstant.builder().roles(List.of("Support")).build());
        }

        @Override
        public Optional<AbilityConstant> ability(String name) {
          return "test_stun".equals(name)
              ? Optional.of(
                  AbilityConstant.builder()
                      .behavior(List.of("Unit Target"))
                      .cd(List.of(10.0))
                      .attrib(List.of(new AbilityAttribute("stun_duration", List.of("3"))))
                      .build())
              : Optional.empty();
        }
      };
    }

    @Bean
    RoleResolver bothStagesResolver(List<StageProfiler> profilers, RoleFitter fitter) {
      return new RoleResolver() {
        @Override
        public boolean supports(GameModeProfile mode) {
          return mode.gameModeId() == TestModes.ABILITY_DRAFT.gameModeId();
        }

        @Override
        public Set<RoleStage> stages() {
          return Set.of(RoleStage.INTENT, RoleStage.FINAL);
        }

        @Override
        public Set<String> requires() {
          return Set.of("ability-draft");
        }

        @Override
        public List<RoleAssignment> resolve(RoleContext context) {
          if (FAIL_INTENT.get() && context.stage() == RoleStage.INTENT) {
            throw new IllegalStateException("intent boom");
          }
          StageProfiler profiler =
              profilers.stream()
                  .filter(p -> p.stage() == context.stage())
                  .findFirst()
                  .orElseThrow();
          List<RoleFitter.RoleFit> fits = fitter.fit(profiler.profile(context.team()));
          List<RoleAssignment> result = new ArrayList<>();
          for (int i = 0; i < fits.size(); i++) {
            result.add(
                new RoleAssignment(
                    context.team().get(i).slot(),
                    fits.get(i).role(),
                    PlayerRoleSource.DERIVED,
                    fits.get(i).confidence(),
                    context.stage().name()));
          }
          return result;
        }
      };
    }
  }

  @Autowired private PlayerRoleAnalyzer analyzer;
  @Autowired private PlayerRoleRepo roleRepo;
  @Autowired private PlayerRoleProfileRepo profileRepo;
  @Autowired private PlayerGameStatisticRepo statisticRepo;
  @Autowired private AbilityDraftPickRepo pickRepo;
  @Autowired private EntityManager entityManager;

  private void stored() {
    entityManager.persist(MatchDomain.builder().id(MATCH).gameModeId(18L).build());
    for (long slot : SLOTS) {
      PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
      stat.setMatchId(MATCH);
      stat.setPlayerSlot(slot);
      stat.setHeroId(slot == 4 ? null : STUNNER_HERO);
      stat.setNetWorth(10000L - slot);
      statisticRepo.save(stat);
    }
    pickRepo.save(
        AbilityDraftPickDomain.builder()
            .matchId(MATCH)
            .pickOrder(1)
            .playerSlot(0L)
            .abilityName("test_stun")
            .finalAbilitySlot(0)
            .ultimate(false)
            .build());
    entityManager.flush();
  }

  private PlayerRoleProfileDomain profile(long slot, String stage) {
    return profileRepo.findByMatchId(MATCH).stream()
        .filter(p -> p.getPlayerSlot() == slot && p.getStage().equals(stage))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aResolverWithBothStagesGetsIntentAndFinalProfilesAndOnlyTheFinalRole() {
    stored();

    analyzer.analyze(MATCH);

    List<PlayerRoleProfileDomain> profiles = profileRepo.findByMatchId(MATCH);
    assertThat(profiles).hasSize(20);
    assertThat(profiles.stream().filter(p -> p.getStage().equals("INTENT"))).hasSize(10);
    List<PlayerRoleDomain> roles = roleRepo.findByMatchId(MATCH);
    assertThat(roles).hasSize(10).allSatisfy(r -> assertThat(r.getEvidence()).isEqualTo("FINAL"));
    for (String stage : List.of("INTENT", "FINAL")) {
      for (boolean dire : new boolean[] {false, true}) {
        assertThat(
                profiles.stream()
                    .filter(p -> p.getStage().equals(stage))
                    .filter(p -> (p.getPlayerSlot() >= 128) == dire)
                    .map(PlayerRoleProfileDomain::getRole))
            .containsExactlyInAnyOrder(1, 2, 3, 4, 5);
      }
    }
  }

  @Test
  void theIntentProfileReadsTheHeroAndTheSkillsDraftedByThatPlayer() {
    stored();

    analyzer.analyze(MATCH);

    PlayerRoleProfileDomain stunner = profile(0, "INTENT");
    assertThat(stunner.getDisable()).isPositive();
    assertThat(stunner.getLane()).isNull();
    assertThat(profile(1, "INTENT").getDisable()).isZero();
    assertThat(profile(4, "INTENT").getDisable()).as("no hero, no skills").isNull();
    assertThat(profile(0, "FINAL").getDisable()).as("nothing observed it").isNull();
  }

  @Test
  void theAnalyzerNamesTheDraftExtractorOnlyBecauseTheResolverDoes() {
    assertThat(analyzer.requires(TestModes.ABILITY_DRAFT))
        .containsExactlyInAnyOrder("activity", "ability-draft");
    assertThat(analyzer.requires(TestModes.ALL_PICK)).containsExactly("activity");
    assertThat(analyzer.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(analyzer.supports(TestModes.ALL_PICK)).isFalse();
  }

  @Test
  void aStageThatThrowsLeavesTheOtherStageAndTheFinalRolesStored() {
    stored();
    FAIL_INTENT.set(true);
    try {
      analyzer.analyze(MATCH);
    } finally {
      FAIL_INTENT.set(false);
    }

    assertThat(roleRepo.findByMatchId(MATCH)).hasSize(10);
    assertThat(profileRepo.findByMatchId(MATCH))
        .hasSize(10)
        .allSatisfy(p -> assertThat(p.getStage()).isEqualTo("FINAL"));
  }

  @Test
  void aSecondRunReplacesBothStages() {
    stored();

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    assertThat(profileRepo.findByMatchId(MATCH)).hasSize(20);
    analyzer.deleteByMatchId(MATCH);
    assertThat(profileRepo.findByMatchId(MATCH)).isEmpty();
  }
}
