package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.composition.TeamCompositionAnalyzer;
import com.ako.dbuff.service.replay.analysis.role.PlayerRoleAnalyzer;
import com.ako.dbuff.service.replay.analysis.role.SkillFunctions;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  FightReasonAnalyzer.class,
  FightContextLoader.class,
  AbsenceReasonContributor.class,
  CarryFarmingElsewhereContributor.class,
  SupportAbsentContributor.class,
  RoleGapContributor.class,
  AbilityNonCastReasonContributor.class,
  DeathReasonContributor.class,
  ReasonProperties.class,
  StoredMatchModes.class,
  GameModeRegistry.class,
  ReplayProcessingProperties.class,
  AllPickProfile.class,
  AbilityDraftProfile.class,
  FightReasonAnalyzerTest.DraftOnly.class,
  FightReasonAnalyzerTest.IntentEverywhere.class
})
class FightReasonAnalyzerTest {
  private static final long ALL_PICK_MATCH = 51L;
  private static final long DRAFT_MATCH = 52L;
  private static final long UNKNOWN_MODE_MATCH = 53L;
  private static final long INTENT_MATCH = 54L;

  @TestConfiguration
  static class IntentEverywhere {
    @Bean
    SkillFunctions skillFunctions() {
      return org.mockito.Mockito.mock(SkillFunctions.class);
    }

    @Bean
    ReasonContributor intentEverywhere() {
      return new ReasonContributor() {
        @Override
        public boolean supports(GameModeProfile mode) {
          return mode.gameModeId() == AbilityDraftProfile.MODE_ID;
        }

        @Override
        public List<Reason> contribute(FightContext context) {
          return List.of(Reason.player("ABILITY_DRAFT", "INTENT_NOT_PLAYED", 0, 0.6, "t"));
        }
      };
    }
  }

  @Test
  void aHeroIntentNotPlayedIsKeptOnlyInTheFirstFightHisTeamLost_drawsSkipped() {
    entityManager.persist(MatchDomain.builder().id(INTENT_MATCH).gameModeId(18L).build());
    String[] winners = {"RADIANT", "DRAW", "DIRE", "DIRE"};
    for (int i = 0; i < winners.length; i++) {
      fightRepo.save(
          FightDomain.builder().matchId(INTENT_MATCH).fightIndex(i).winner(winners[i]).build());
    }
    entityManager.flush();

    analyzer.analyze(INTENT_MATCH);

    assertThat(reasonRepo.findByMatchId(INTENT_MATCH))
        .filteredOn(r -> r.getCode().equals("INTENT_NOT_PLAYED"))
        .extracting(FightReasonDomain::getFightIndex, FightReasonDomain::getSubjectSlot)
        .containsExactly(tuple(2, 0L));
    assertThat(analyzer.version()).isEqualTo(5);
  }

  @TestConfiguration
  static class DraftOnly {
    @Bean
    ReasonContributor draftOnly() {
      return new ReasonContributor() {
        @Override
        public boolean supports(GameModeProfile mode) {
          return mode.gameModeId() == AbilityDraftProfile.MODE_ID;
        }

        @Override
        public List<Reason> contribute(FightContext context) {
          return List.of(
              Reason.player("ABILITY_DRAFT", "WEAK_COMBINATION", 0, 0.6, "test"),
              Reason.team("ABILITY_DRAFT", "NO_INITIATOR", TeamSide.RADIANT, 0.5, "x".repeat(2000)),
              Reason.team("ABILITY_DRAFT", "NO_INITIATOR", TeamSide.DIRE, 0.5, "x".repeat(2000)));
        }
      };
    }
  }

  @Test
  void aTeamReasonOfEachTeamWithTheSameCodeIsKept_andLongEvidenceIsCutToTheColumn() {
    stored(DRAFT_MATCH, 18);

    analyzer.analyze(DRAFT_MATCH);

    List<FightReasonDomain> team =
        reasonRepo.findByMatchId(DRAFT_MATCH).stream()
            .filter(r -> r.getCode().equals("NO_INITIATOR"))
            .toList();
    assertThat(team)
        .extracting(FightReasonDomain::getFightIndex, FightReasonDomain::getTeam)
        .containsExactlyInAnyOrder(
            tuple(0, "RADIANT"), tuple(0, "DIRE"), tuple(1, "RADIANT"), tuple(1, "DIRE"));
    assertThat(team)
        .allSatisfy(
            r -> {
              assertThat(r.getSubjectSlot()).isNull();
              assertThat(r.getScope()).isEqualTo("TEAM");
              assertThat(r.getEvidence()).hasSize(1000).endsWith("...");
            });
  }

  @Test
  void requiresEveryAnalyzerItReads_andTheDraftExtractorOnlyForTheDraftMode() {
    assertThat(analyzer.requires())
        .containsExactlyInAnyOrder(
            "fights", "fight-abilities", "deaths", "roles", "composition", "runes");
    assertThat(analyzer.requires(new AllPickProfile()))
        .contains("runes")
        .doesNotContain("ability-draft");
    assertThat(analyzer.requires(new AbilityDraftProfile()))
        .contains("ability-draft", "roles", "runes");
  }

  @Test
  void aFightWithNoIndexIsSkipped() {
    stored(ALL_PICK_MATCH, 22);
    fightRepo.save(FightDomain.builder().matchId(ALL_PICK_MATCH).build());
    entityManager.flush();

    analyzer.analyze(ALL_PICK_MATCH);

    assertThat(reasonRepo.findByMatchId(ALL_PICK_MATCH)).isNotEmpty();
  }

  @Test
  void runsAfterTheRoleAndCompositionAnalyzers() {
    int reasons = AnnotationUtils.findAnnotation(FightReasonAnalyzer.class, Order.class).value();

    assertThat(reasons)
        .isGreaterThan(
            AnnotationUtils.findAnnotation(PlayerRoleAnalyzer.class, Order.class).value())
        .isGreaterThan(
            AnnotationUtils.findAnnotation(TeamCompositionAnalyzer.class, Order.class).value());
  }

  @Autowired private FightReasonAnalyzer analyzer;
  @Autowired private FightReasonRepo reasonRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightPlayerRepo fightPlayerRepo;
  @Autowired private FightAbilityUseRepo useRepo;
  @Autowired private DeathAnalysisRepo deathRepo;
  @Autowired private EntityManager entityManager;

  private void stored(long matchId, long mode) {
    entityManager.persist(MatchDomain.builder().id(matchId).gameModeId(mode).build());
    fightRepo.save(FightDomain.builder().matchId(matchId).fightIndex(0).build());
    fightRepo.save(FightDomain.builder().matchId(matchId).fightIndex(1).build());
    fightPlayerRepo.save(
        FightPlayerDomain.builder()
            .matchId(matchId)
            .fightIndex(0)
            .playerSlot(0L)
            .presence("ABSENT_FAR")
            .absenceReason("NO_TP")
            .build());
    fightPlayerRepo.save(
        FightPlayerDomain.builder()
            .matchId(matchId)
            .fightIndex(1)
            .playerSlot(128L)
            .presence("ACTIVE")
            .build());
    useRepo.save(
        FightAbilityUseDomain.builder()
            .matchId(matchId)
            .fightIndex(1)
            .playerSlot(128L)
            .abilityName("item_blink")
            .kind("ITEM")
            .nonCastReason("ON_COOLDOWN")
            .build());
    deathRepo.save(
        DeathAnalysisDomain.builder()
            .matchId(matchId)
            .playerSlot(128L)
            .deathIndex(0)
            .deathSeconds(300.0)
            .killerKind("HERO")
            .fightIndex(1)
            .reason("TOWER")
            .reincarnated(false)
            .build());
    deathRepo.save(
        DeathAnalysisDomain.builder()
            .matchId(matchId)
            .playerSlot(129L)
            .deathIndex(0)
            .deathSeconds(100.0)
            .killerKind("TOWER")
            .reason("TOWER")
            .reincarnated(false)
            .build());
    entityManager.flush();
  }

  @Test
  void everyFightGetsTheReasonsOfItsOwnRows_andASecondRunReplacesTheFirst() {
    stored(ALL_PICK_MATCH, 22);

    analyzer.analyze(ALL_PICK_MATCH);
    analyzer.analyze(ALL_PICK_MATCH);

    List<FightReasonDomain> rows = reasonRepo.findByMatchId(ALL_PICK_MATCH);
    assertThat(rows)
        .extracting(r -> r.getFightIndex() + "/" + r.getSubjectSlot() + "/" + r.getCode())
        .containsExactlyInAnyOrder("0/0/NO_TP", "1/128/ITEM_ON_COOLDOWN", "1/128/TOWER");
    assertThat(rows)
        .allSatisfy(
            r -> {
              assertThat(r.getScope()).isEqualTo("PLAYER");
              assertThat(r.getSource()).isEqualTo("COMMON");
              assertThat(r.getAnalysisVersion()).isEqualTo(5);
            });
    assertThat(rows)
        .filteredOn(r -> r.getSubjectSlot() == 128L)
        .allSatisfy(r -> assertThat(r.getTeam()).isEqualTo("DIRE"));
  }

  @Test
  void roleAwareReasonsAreStoredFromRolesAndCompositionAndSkippedWithoutThem() {
    stored(ALL_PICK_MATCH, 22);
    fightRepo.findByMatchId(ALL_PICK_MATCH).stream()
        .filter(f -> f.getFightIndex() == 0)
        .forEach(
            f -> {
              f.setWinner("DIRE");
              fightRepo.save(f);
            });
    fightPlayerRepo.save(
        FightPlayerDomain.builder()
            .matchId(ALL_PICK_MATCH)
            .fightIndex(0)
            .playerSlot(1L)
            .presence("ABSENT_FARMING")
            .activityAtStart("JUNGLE_FARM")
            .build());
    entityManager.persist(
        PlayerRoleDomain.builder().matchId(ALL_PICK_MATCH).playerSlot(1L).role(1).build());
    entityManager.persist(
        TeamCompositionDomain.builder()
            .matchId(ALL_PICK_MATCH)
            .team("RADIANT")
            .stage("FINAL")
            .tokens("CARRIES_1,NO_DISABLE")
            .build());
    entityManager.flush();

    analyzer.analyze(ALL_PICK_MATCH);

    assertThat(reasonRepo.findByMatchId(ALL_PICK_MATCH))
        .filteredOn(r -> r.getFightIndex() == 0)
        .extracting(
            r -> r.getScope() + "/" + r.getTeam() + "/" + r.getSubjectSlot() + "/" + r.getCode())
        .contains("PLAYER/RADIANT/1/CARRY_FARMING_ELSEWHERE", "TEAM/RADIANT/null/NO_DISABLE")
        .doesNotContain("PLAYER/RADIANT/1/SUPPORT_ABSENT");
    assertThat(reasonRepo.findByMatchId(ALL_PICK_MATCH))
        .filteredOn(r -> r.getFightIndex() == 1)
        .extracting(FightReasonDomain::getCode)
        .doesNotContain("NO_DISABLE", "CARRY_FARMING_ELSEWHERE");
  }

  @Test
  void aContributorOfAnotherModeIsLeftOut_andOneOfTheModeRunsWithoutChangingTheAnalyzer() {
    stored(ALL_PICK_MATCH, 22);
    stored(DRAFT_MATCH, 18);

    analyzer.analyze(ALL_PICK_MATCH);
    analyzer.analyze(DRAFT_MATCH);

    assertThat(reasonRepo.findByMatchId(ALL_PICK_MATCH))
        .extracting(FightReasonDomain::getCode)
        .doesNotContain("WEAK_COMBINATION");
    assertThat(reasonRepo.findByMatchId(DRAFT_MATCH))
        .filteredOn(r -> r.getCode().equals("WEAK_COMBINATION"))
        .hasSize(2)
        .allSatisfy(r -> assertThat(r.getSource()).isEqualTo("ABILITY_DRAFT"));
  }

  @Test
  void aMatchWithoutAnActiveModeGetsNothing() {
    stored(UNKNOWN_MODE_MATCH, 99);

    analyzer.analyze(UNKNOWN_MODE_MATCH);

    assertThat(reasonRepo.findByMatchId(UNKNOWN_MODE_MATCH)).isEmpty();
  }

  @Test
  void deleteRemovesOnlyThatMatch() {
    stored(ALL_PICK_MATCH, 22);
    stored(DRAFT_MATCH, 18);
    analyzer.analyze(ALL_PICK_MATCH);
    analyzer.analyze(DRAFT_MATCH);

    analyzer.deleteByMatchId(ALL_PICK_MATCH);

    assertThat(reasonRepo.findByMatchId(ALL_PICK_MATCH)).isEmpty();
    assertThat(reasonRepo.findByMatchId(DRAFT_MATCH)).isNotEmpty();
  }
}
