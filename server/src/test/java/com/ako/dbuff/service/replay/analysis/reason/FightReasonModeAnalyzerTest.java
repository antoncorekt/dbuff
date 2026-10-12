package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.IntentProfileCalculator;
import com.ako.dbuff.service.replay.analysis.role.RoleOverrides;
import com.ako.dbuff.service.replay.analysis.role.RoleProfileProperties;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** The mode contributors run through the unchanged analyzer, each only for its own mode. */
@DataJpaTest
@ActiveProfiles("test")
@Import({
  FightReasonAnalyzer.class,
  IntentNotPlayedContributor.class,
  WeakCombinationContributor.class,
  StrongSkillLeftContributor.class,
  HeroRoleMismatchContributor.class,
  ReasonProperties.class,
  StoredMatchModes.class,
  GameModeRegistry.class,
  ReplayProcessingProperties.class,
  AllPickProfile.class,
  AbilityDraftProfile.class,
  FightReasonModeAnalyzerTest.Kits.class
})
class FightReasonModeAnalyzerTest {
  private static final long DRAFT_MATCH = 61L;
  private static final long ALL_PICK_MATCH = 62L;

  @TestConfiguration
  static class Kits {
    @Bean
    HeroKits kits() {
      return new ModeTestKits(
          Map.of(
              5L, ModeTestKits.hero(5, "Crystal Maiden", "Support"),
              64L, ModeTestKits.hero(64, "Jakiro")),
          ModeTestKits.fixtureAbilities());
    }

    @Bean
    IntentProfileCalculator calculator() {
      return new IntentProfileCalculator(new RoleProfileProperties(), RoleOverrides.NONE);
    }
  }

  @Autowired private FightReasonAnalyzer analyzer;
  @Autowired private FightReasonRepo reasonRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightPlayerRepo fightPlayerRepo;
  @Autowired private EntityManager entityManager;

  private void match(long matchId, long mode, int heroId, String presence) {
    entityManager.persist(MatchDomain.builder().id(matchId).gameModeId(mode).build());
    fightRepo.save(FightDomain.builder().matchId(matchId).fightIndex(0).winner("DIRE").build());
    fightPlayerRepo.save(
        FightPlayerDomain.builder()
            .matchId(matchId)
            .fightIndex(0)
            .playerSlot(0L)
            .presence(presence)
            .build());
    entityManager.persist(
        PlayerMatchStatisticDomain.builder()
            .matchId(matchId)
            .playerSlot(0L)
            .heroId((long) heroId)
            .build());
  }

  private void draftData() {
    entityManager.persist(profile("INTENT", 1));
    entityManager.persist(profile("FINAL", 3));
    entityManager.persist(
        AbilityDraftPoolDomain.builder()
            .matchId(DRAFT_MATCH)
            .poolIndex(0)
            .abilityName("sven_storm_bolt")
            .build());
  }

  private static PlayerRoleProfileDomain profile(String stage, int role) {
    return PlayerRoleProfileDomain.builder()
        .matchId(DRAFT_MATCH)
        .playerSlot(0L)
        .stage(stage)
        .role(role)
        .initiation(0.1)
        .disable(0.2)
        .build();
  }

  @Test
  void anAbilityDraftMatchGetsTheDraftReasonsAndNoAllPickOne() {
    match(DRAFT_MATCH, 18, 64, "ACTIVE");
    draftData();
    entityManager.persist(
        PlayerRoleDomain.builder().matchId(DRAFT_MATCH).playerSlot(0L).role(3).build());
    entityManager.flush();

    analyzer.analyze(DRAFT_MATCH);

    assertThat(reasonRepo.findByMatchId(DRAFT_MATCH))
        .extracting(
            r -> r.getScope() + "/" + r.getTeam() + "/" + r.getSubjectSlot() + "/" + r.getCode())
        .containsExactlyInAnyOrder(
            "PLAYER/RADIANT/0/INTENT_NOT_PLAYED",
            "TEAM/RADIANT/null/WEAK_COMBINATION",
            "TEAM/RADIANT/null/STRONG_SKILL_LEFT");
    assertThat(reasonRepo.findByMatchId(DRAFT_MATCH))
        .allSatisfy(r -> assertThat(r.getSource()).isEqualTo("ABILITY_DRAFT"));
  }

  @Test
  void anAllPickMatchGetsTheHeroRoleMismatchAndNoDraftReason() {
    match(ALL_PICK_MATCH, 22, 5, "ACTIVE");
    entityManager.persist(
        PlayerRoleDomain.builder().matchId(ALL_PICK_MATCH).playerSlot(0L).role(1).build());
    entityManager.flush();

    analyzer.analyze(ALL_PICK_MATCH);

    List<FightReasonDomain> rows = reasonRepo.findByMatchId(ALL_PICK_MATCH);
    assertThat(rows)
        .extracting(FightReasonDomain::getCode, FightReasonDomain::getSource)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("HERO_ROLE_MISMATCH", "ALL_PICK"));
    assertThat(rows.getFirst().getEvidence()).isEqualTo("Crystal Maiden (Support) played carry");
  }
}
