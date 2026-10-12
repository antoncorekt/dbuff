package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.mode.TestModes;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  PlayerRoleAnalyzer.class,
  StoredActivity.class,
  AllPickRoleResolver.class,
  StoredMatchModes.class,
  GameModeRegistry.class,
  ReplayProcessingProperties.class,
  ReplayAnalysisProperties.class,
  AllPickProfile.class,
  AbilityDraftProfile.class,
  RoleProfileTestConfig.class,
  PlayerRoleAnalyzerTest.NoKits.class
})
class PlayerRoleAnalyzerTest {

  @TestConfiguration
  static class NoKits {
    @Bean
    HeroKits heroKits() {
      return new HeroKits() {
        @Override
        public Optional<HeroConstant> hero(long heroId) {
          return Optional.empty();
        }

        @Override
        public Optional<AbilityConstant> ability(String name) {
          return Optional.empty();
        }
      };
    }
  }

  private static final long ALL_PICK_MATCH = 31L;
  private static final long ABILITY_DRAFT_MATCH = 32L;
  private static final long[] SLOTS = {0, 1, 2, 3, 4, 128, 129, 130, 131, 132};
  private static final long[] LANE_ROLES = {1, 2, 3, 3, 1};
  private static final long[] WORTH = {24000, 18000, 13000, 7000, 5000};

  @Autowired private PlayerRoleAnalyzer analyzer;
  @Autowired private RoleFitter fitter;
  @Autowired private PlayerRoleRepo roleRepo;
  @Autowired private PlayerRoleProfileRepo profileRepo;
  @Autowired private PlayerTimelineRepo timelineRepo;
  @Autowired private PlayerGameStatisticRepo statisticRepo;
  @Autowired private EntityManager entityManager;

  private void stored(long matchId, long mode) {
    entityManager.persist(MatchDomain.builder().id(matchId).gameModeId(mode).build());
    for (int i = 0; i < SLOTS.length; i++) {
      PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
      stat.setMatchId(matchId);
      stat.setPlayerSlot(SLOTS[i]);
      stat.setLaneRole(LANE_ROLES[i % 5]);
      stat.setNetWorth(WORTH[i % 5]);
      statisticRepo.save(stat);
    }
    entityManager.flush();
  }

  @Test
  void storesOneRolePerPlayerAndEachRoleOncePerTeam() {
    stored(ALL_PICK_MATCH, 22);

    analyzer.analyze(ALL_PICK_MATCH);

    List<PlayerRoleDomain> rows = roleRepo.findByMatchId(ALL_PICK_MATCH);
    assertThat(rows).hasSize(10);
    assertThat(rows)
        .allSatisfy(
            r -> {
              assertThat(r.getSource()).isEqualTo("DERIVED");
              assertThat(r.getAnalysisVersion()).isEqualTo(analyzer.version());
              assertThat(r.getEvidence()).isNotBlank();
            });
    for (boolean dire : new boolean[] {false, true}) {
      assertThat(
              rows.stream()
                  .filter(r -> (r.getPlayerSlot() >= 128) == dire)
                  .map(PlayerRoleDomain::getRole))
          .containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    }
  }

  @Test
  void aSecondRunReplacesTheFirst() {
    stored(ALL_PICK_MATCH, 22);

    analyzer.analyze(ALL_PICK_MATCH);
    analyzer.analyze(ALL_PICK_MATCH);

    assertThat(roleRepo.findByMatchId(ALL_PICK_MATCH)).hasSize(10);
    assertThat(profileRepo.findByMatchId(ALL_PICK_MATCH)).hasSize(10);
  }

  @Test
  void theStoredDamageHealingWardsAndDamageTakenReachTheFinalProfile() {
    entityManager.persist(MatchDomain.builder().id(ALL_PICK_MATCH).gameModeId(22L).build());
    for (int i = 0; i < SLOTS.length; i++) {
      PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
      stat.setMatchId(ALL_PICK_MATCH);
      stat.setPlayerSlot(SLOTS[i]);
      stat.setLaneRole(LANE_ROLES[i % 5]);
      stat.setNetWorth(WORTH[i % 5]);
      boolean first = i % 5 == 0;
      stat.setHeroDamage(first ? 100L : 0L);
      stat.setHeroHealing(first ? 0L : 10L);
      stat.setDamageTaken(first ? 50L : 0L);
      stat.setObsPlaced(first ? null : 1L);
      stat.setSenPlaced(first ? null : 1L);
      statisticRepo.save(stat);
    }
    entityManager.flush();

    analyzer.analyze(ALL_PICK_MATCH);

    for (PlayerRoleProfileDomain p : profileRepo.findByMatchId(ALL_PICK_MATCH)) {
      boolean first = p.getPlayerSlot() % 128 == 0;
      assertThat(p.getDamage()).isEqualTo(first ? 1.0 : 0.0);
      assertThat(p.getDurability()).isEqualTo(first ? 1.0 : 0.0);
      assertThat(p.getSaveHeal()).isEqualTo(first ? 0.0 : 0.5);
      assertThat(p.getVisionDuty()).isEqualTo(first ? null : 0.5);
    }
  }

  @Test
  void allPickStoresFinalProfilesNextToTheRolesAndNoIntentRows() {
    stored(ALL_PICK_MATCH, 22);

    analyzer.analyze(ALL_PICK_MATCH);

    List<PlayerRoleProfileDomain> profiles = profileRepo.findByMatchId(ALL_PICK_MATCH);
    assertThat(profiles).hasSize(10).extracting(p -> p.getStage()).containsOnly("FINAL");
    for (PlayerRoleDomain role : roleRepo.findByMatchId(ALL_PICK_MATCH)) {
      PlayerRoleProfileDomain profile =
          profiles.stream()
              .filter(p -> p.getPlayerSlot().equals(role.getPlayerSlot()))
              .findFirst()
              .orElseThrow();
      assertThat(profile.getRole()).isEqualTo(role.getRole());
      assertThat(profile.getConfidence()).isEqualTo(role.getConfidence());
      assertThat(profile.getEvidence()).isEqualTo(role.getEvidence());
      assertThat(profile.getAnalysisVersion()).isEqualTo(analyzer.version());
      assertThat(profile.getFarmPriority()).isBetween(0.0, 1.0);
      assertThat(profile.getLane()).isNotNull();
      assertThat(profile.getInitiation()).isNull();
      assertThat(profile.getRoleScores().split(",")).hasSize(5);
      double[] scores =
          java.util.Arrays.stream(profile.getRoleScores().split(","))
              .mapToDouble(Double::parseDouble)
              .toArray();
      assertThat(profile.getFitRole()).isEqualTo(RoleFitter.bestRole(scores));
      FunctionProfile read =
          new FunctionProfile(
              LanePosition.valueOf(profile.getLane()),
              profile.getFarmPriority(),
              profile.getJungle(),
              profile.getRoaming(),
              profile.getVisionDuty(),
              profile.getInitiation(),
              profile.getDisable(),
              profile.getSaveHeal(),
              profile.getDamage(),
              profile.getDurability());
      assertThat(profile.getRoleScores())
          .isEqualTo(
              java.util.Arrays.stream(fitter.scores(read, RoleStage.INTENT))
                  .mapToObj(s -> String.format(java.util.Locale.ROOT, "%.2f", s))
                  .collect(java.util.stream.Collectors.joining(",")));
    }
  }

  @Test
  void anAbilityDraftMatchGetsNoRoles() {
    stored(ABILITY_DRAFT_MATCH, 18);

    assertThat(analyzer.supports(TestModes.ABILITY_DRAFT)).isFalse();
    analyzer.analyze(ABILITY_DRAFT_MATCH);

    assertThat(roleRepo.findByMatchId(ABILITY_DRAFT_MATCH)).isEmpty();
    assertThat(profileRepo.findByMatchId(ABILITY_DRAFT_MATCH)).isEmpty();
  }

  @Test
  void aTeamWithoutFivePlayersGetsNoRoles() {
    stored(ALL_PICK_MATCH, 22);
    statisticRepo.deleteAll(
        statisticRepo.findAllByMatchId(ALL_PICK_MATCH).stream()
            .filter(s -> s.getPlayerSlot() == 132)
            .toList());
    entityManager.flush();

    analyzer.analyze(ALL_PICK_MATCH);

    assertThat(roleRepo.findByMatchId(ALL_PICK_MATCH)).hasSize(5);
  }

  @Test
  void deleteRemovesTheRoles() {
    stored(ALL_PICK_MATCH, 22);
    analyzer.analyze(ALL_PICK_MATCH);

    analyzer.deleteByMatchId(ALL_PICK_MATCH);

    assertThat(roleRepo.findByMatchId(ALL_PICK_MATCH)).isEmpty();
    assertThat(profileRepo.findByMatchId(ALL_PICK_MATCH)).isEmpty();
  }

  @Test
  void namesTheActivityAnalyzerAsAnInput() {
    assertThat(analyzer.requires()).contains("activity");
  }

  @Test
  void aTeamWithAForeignSlotIsSkippedAndTheOtherStillGetsRoles() {
    stored(ALL_PICK_MATCH, 22);
    PlayerMatchStatisticDomain stray = new PlayerMatchStatisticDomain();
    stray.setMatchId(ALL_PICK_MATCH);
    stray.setPlayerSlot(5L);
    statisticRepo.save(stray);
    entityManager.flush();

    analyzer.analyze(ALL_PICK_MATCH);

    assertThat(roleRepo.findByMatchId(ALL_PICK_MATCH))
        .hasSize(5)
        .allSatisfy(r -> assertThat(r.getPlayerSlot()).isGreaterThanOrEqualTo(128));
  }

  @Test
  void aMissingMinuteTakesTheLatestEarlierWorthAndAMissingSeriesLowersConfidence() {
    stored(ALL_PICK_MATCH, 22);
    for (int i = 0; i < 9; i++) {
      for (int minute = 0; minute < 14; minute++) {
        if (i == 0 && minute == 12) {
          continue;
        }
        timelineRepo.save(
            PlayerTimelineDomain.builder()
                .matchId(ALL_PICK_MATCH)
                .playerSlot(SLOTS[i])
                .gameMinute(minute)
                .networth(WORTH[i % 5])
                .build());
      }
    }
    entityManager.flush();

    analyzer.analyze(ALL_PICK_MATCH);

    List<PlayerRoleDomain> rows = roleRepo.findByMatchId(ALL_PICK_MATCH);
    PlayerRoleDomain gap =
        rows.stream().filter(r -> r.getPlayerSlot() == 0).findFirst().orElseThrow();
    PlayerRoleDomain none =
        rows.stream().filter(r -> r.getPlayerSlot() == 132).findFirst().orElseThrow();
    assertThat(gap.getEvidence()).contains("1.0 final and 1.0 in the laning phase");
    assertThat(none.getEvidence()).contains("unknown in the laning phase");
    assertThat(none.getConfidence()).isLessThanOrEqualTo(0.5);
  }
}
