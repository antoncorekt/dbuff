package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.dao.model.BuildFitDomain;
import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.repo.BuildFitRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
  BuildFitAnalyzer.class,
  BuildFitJudge.class,
  BuildFitProperties.class,
  RoleNeedsFile.class,
  BuildFitAnalyzerTest.Kits.class
})
class BuildFitAnalyzerTest {
  private static final long MATCH = 51L;
  private static final long[] SLOTS = {0, 1, 2, 3, 4, 128, 129, 130, 131, 132};

  @TestConfiguration
  static class Kits {
    @Bean
    HeroKits heroKits() {
      return new HeroKits() {
        @Override
        public Optional<HeroConstant> hero(long heroId) {
          return Optional.of(HeroConstant.builder().localized_name("Hero" + heroId).build());
        }

        @Override
        public Optional<AbilityConstant> ability(String name) {
          return Optional.empty();
        }
      };
    }
  }

  @Autowired private BuildFitAnalyzer analyzer;
  @Autowired private BuildFitRepo buildFitRepo;
  @Autowired private PlayerRoleProfileRepo profileRepo;
  @Autowired private PlayerGameStatisticRepo statisticRepo;
  @Autowired private DeathAnalysisRepo deathRepo;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void stats() {
    for (int i = 0; i < SLOTS.length; i++) {
      statisticRepo.save(
          PlayerMatchStatisticDomain.builder()
              .matchId(MATCH)
              .playerSlot(SLOTS[i])
              .heroId(10L + i)
              .netWorth(30000L - (i % 5) * 4000L)
              .build());
    }
  }

  private void profile(long slot, String stage, int role, Double farm, Double escape) {
    profileRepo.save(
        PlayerRoleProfileDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .stage(stage)
            .role(role)
            .farmPriority(farm)
            .damage(0.9)
            .disable(0.9)
            .saveHeal(0.9)
            .initiation(0.9)
            .durability(0.9)
            .escape(escape)
            .build());
  }

  private void aloneDeaths(long slot, int count) {
    for (int i = 0; i < count; i++) {
      deathRepo.save(
          DeathAnalysisDomain.builder()
              .matchId(MATCH)
              .playerSlot(slot)
              .deathIndex(i)
              .isolated(true)
              .build());
    }
    deathRepo.save(
        DeathAnalysisDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .deathIndex(99)
            .isolated(false)
            .build());
    entityManager.flush();
  }

  private BuildFitDomain row(long slot, String stage) {
    return buildFitRepo.findByMatchId(MATCH).stream()
        .filter(r -> r.getPlayerSlot() == slot && r.getStage().equals(stage))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aPlayerWithTheSameRoleGetsOneVerdictAndTheMatchBacksTheGap() {
    profile(0, "INTENT", 1, 0.8, 0.0);
    profile(0, "FINAL", 1, 0.8, null);
    aloneDeaths(0, 3);

    analyzer.analyze(MATCH);

    assertThat(buildFitRepo.findByMatchId(MATCH)).hasSize(1);
    BuildFitDomain verdict = row(0, "BOTH");
    assertThat(verdict.getRole()).isEqualTo(1);
    assertThat(verdict.getMissing()).isEqualTo("ESCAPE");
    assertThat(verdict.getStated()).isEqualTo("ESCAPE");
    assertThat(verdict.getEvidence()).isEqualTo("Hero10 had no escape and died alone 3 times.");
    assertThat(verdict.getAnalysisVersion()).isEqualTo(analyzer.version());
  }

  @Test
  void aMissingEscapeWithoutDeathsAloneIsMissingButNotStated() {
    profile(0, "INTENT", 1, 0.8, 0.0);
    profile(0, "FINAL", 1, 0.8, null);

    analyzer.analyze(MATCH);

    BuildFitDomain verdict = row(0, "BOTH");
    assertThat(verdict.getMissing()).isEqualTo("ESCAPE");
    assertThat(verdict.getStated()).isEmpty();
    assertThat(verdict.getEvidence()).isEqualTo("Hero10's build fit the carry role.");
  }

  @Test
  void aPlayerWhoPlayedAnotherRoleGetsBothVerdicts() {
    profile(4, "INTENT", 5, 0.1, 0.9);
    profile(4, "FINAL", 1, 0.1, null);

    analyzer.analyze(MATCH);

    assertThat(buildFitRepo.findByMatchId(MATCH)).hasSize(2);
    assertThat(row(4, "INTENT").getRole()).isEqualTo(5);
    BuildFitDomain played = row(4, "FINAL");
    assertThat(played.getRole()).isEqualTo(1);
    assertThat(played.getMissing()).isEqualTo("FARM_PRIORITY");
    assertThat(played.getStated()).isEqualTo("FARM_PRIORITY");
    assertThat(played.getEvidence())
        .isEqualTo(
            "Played carry: Hero14 had only weak farming skills and finished 5th of 5 in team net"
                + " worth.");
  }

  @Test
  void anOlderProfileWithoutAnEscapeValueStillGetsItsFarmVerdict() {
    profile(4, "INTENT", 1, 0.1, null);
    profile(4, "FINAL", 1, 0.1, null);

    analyzer.analyze(MATCH);

    BuildFitDomain verdict = row(4, "BOTH");
    assertThat(verdict.getMissing()).isEqualTo("FARM_PRIORITY");
    assertThat(verdict.getStated()).isEqualTo("FARM_PRIORITY");
  }

  @Test
  void aPlayerWithoutAFinalProfileGetsAnIntentVerdictOnly() {
    profile(0, "INTENT", 1, 0.8, 0.9);

    analyzer.analyze(MATCH);

    assertThat(buildFitRepo.findByMatchId(MATCH))
        .extracting(BuildFitDomain::getStage)
        .containsExactly("INTENT");
  }

  @Test
  void aProfileWithoutAKnownRoleIsLeftOut() {
    profile(0, "INTENT", 9, 0.8, 0.9);
    profile(1, "INTENT", 1, 0.8, 0.9);
    profile(1, "FINAL", 0, 0.8, null);

    analyzer.analyze(MATCH);

    assertThat(buildFitRepo.findByMatchId(MATCH))
        .extracting(BuildFitDomain::getPlayerSlot, BuildFitDomain::getStage)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, "INTENT"));
  }

  @Test
  void aBuildWithNothingKnownOrNoIntentRowGetsNothing() {
    profileRepo.save(
        PlayerRoleProfileDomain.builder()
            .matchId(MATCH)
            .playerSlot(0L)
            .stage("INTENT")
            .role(1)
            .build());
    profile(1, "FINAL", 2, 0.8, null);

    analyzer.analyze(MATCH);

    assertThat(buildFitRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void aRerunReplacesTheRowsAndDeleteRemovesThem() {
    profile(0, "INTENT", 1, 0.8, 0.9);
    profile(0, "FINAL", 1, 0.8, null);

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);
    assertThat(buildFitRepo.findByMatchId(MATCH)).hasSize(1);

    analyzer.deleteByMatchId(MATCH);
    assertThat(buildFitRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void onlyAbilityDraftIsSupported() {
    assertThat(analyzer.supports(new AbilityDraftProfile())).isTrue();
    assertThat(analyzer.supports(new AllPickProfile())).isFalse();
    assertThat(analyzer.requires()).containsExactly("roles");
    assertThat(List.of(analyzer.name())).containsExactly("build-fit");
  }
}
