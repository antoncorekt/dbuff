package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.FightExplanationRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.FightVisionRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourObjectiveAnalyzer;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourRotationAnalyzer;
import com.ako.dbuff.service.replay.analysis.fightloss.FightLossAnalyzer;
import com.ako.dbuff.service.replay.analysis.fightloss.FightLossProperties;
import com.ako.dbuff.service.replay.analysis.fightloss.ReasonRanking;
import com.ako.dbuff.service.replay.analysis.importance.ImportantFightAnalyzer;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.vision.VisionAnalyzer;
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
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  LateFightReasonAnalyzer.class,
  FightContextLoader.class,
  VisionReasonContributor.class,
  RotationReasonContributor.class,
  FightLossAnalyzer.class,
  FightLossProperties.class,
  ReasonProperties.class,
  StoredMatchModes.class,
  GameModeRegistry.class,
  ReplayProcessingProperties.class,
  AllPickProfile.class,
  AbilityDraftProfile.class,
  LateFightReasonAnalyzerTest.Kits.class
})
class LateFightReasonAnalyzerTest {
  private static final long MATCH = 71L;

  @TestConfiguration
  static class Kits {
    @Bean
    HeroKits kits() {
      return new ModeTestKits(Map.of(3L, ModeTestKits.hero(3, "Windranger")), Map.of());
    }
  }

  @Autowired private LateFightReasonAnalyzer analyzer;
  @Autowired private FightLossAnalyzer lossAnalyzer;
  @Autowired private FightReasonRepo reasonRepo;
  @Autowired private FightExplanationRepo explanationRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightPlayerRepo fightPlayerRepo;
  @Autowired private FightVisionRepo visionRepo;
  @Autowired private TeamRotationRepo rotationRepo;
  @Autowired private EntityManager entityManager;

  private FightReasonDomain firstPass() {
    return reasonRepo.save(
        FightReasonDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .team("DIRE")
            .scope("PLAYER")
            .subjectSlot(129L)
            .code("NO_TP")
            .severity(0.7)
            .source("COMMON")
            .analysisVersion(4)
            .build());
  }

  private void stored() {
    entityManager.persist(MatchDomain.builder().id(MATCH).gameModeId(22L).build());
    fightRepo.save(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .startSeconds(1000.0)
            .endSeconds(1030.0)
            .winner("RADIANT")
            .build());
    entityManager.persist(
        PlayerMatchStatisticDomain.builder().matchId(MATCH).playerSlot(129L).heroId(3L).build());
    fightPlayerRepo.save(
        FightPlayerDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .playerSlot(129L)
            .presence("ABSENT_FAR")
            .build());
    visionRepo.save(
        FightVisionDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .team("RADIANT")
            .verdict("FINE")
            .activeObservers(2)
            .build());
    visionRepo.save(
        FightVisionDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .team("DIRE")
            .lost(true)
            .verdict("NO_VISION")
            .activeObservers(0)
            .build());
    rotationRepo.save(
        TeamRotationDomain.builder()
            .matchId(MATCH)
            .team("DIRE")
            .playerSlot(129L)
            .startSeconds(900)
            .endSeconds(970)
            .fromZone("LANE")
            .fromLane("TOP")
            .toZone("LANE")
            .toLane("BOTTOM")
            .verdict("WASTED")
            .reasons("INCOME_DROP")
            .build());
    entityManager.flush();
  }

  private List<FightReasonDomain> late() {
    return reasonRepo.findByMatchId(MATCH).stream()
        .filter(r -> !"COMMON".equals(r.getSource()))
        .toList();
  }

  @Test
  void appendsTheVisionAndRotationReasonsAndLeavesTheFirstPassRowsAlone() {
    stored();
    Long firstId = firstPass().getId();

    analyzer.analyze(MATCH);

    assertThat(late())
        .extracting(FightReasonDomain::getCode, FightReasonDomain::getSource)
        .containsExactlyInAnyOrder(
            tuple("NO_VISION_BEFORE_FIGHT", "VISION"),
            tuple("ROTATION_WASTED_BEFORE_FIGHT", "ROTATION"));
    assertThat(reasonRepo.findById(firstId))
        .get()
        .satisfies(r -> assertThat(r.getAnalysisVersion()).isEqualTo(4));
  }

  @Test
  void aSecondRunReplacesItsOwnRowsOnly() {
    stored();
    firstPass();

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    assertThat(late()).hasSize(2);
    assertThat(reasonRepo.findByMatchId(MATCH)).hasSize(3);
  }

  @Test
  void theStoredReasonCountOfTheLossIsRecountedWithTheNewRows() {
    stored();
    firstPass();
    lossAnalyzer.analyze(MATCH);
    assertThat(explanationRepo.findByMatchId(MATCH))
        .extracting(FightExplanationDomain::getReasonCount)
        .containsExactly(1);

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    assertThat(explanationRepo.findByMatchId(MATCH))
        .extracting(FightExplanationDomain::getReasonCount)
        .containsExactly(3);
  }

  @Test
  void theNewRowsAreRankedWithTheOthers_playerReasonsFirstThenTheTeamReason() {
    stored();
    firstPass();
    analyzer.analyze(MATCH);

    List<FightReasonDomain> ranked =
        ReasonRanking.rank(reasonRepo.findByMatchId(MATCH), 1000.0, new FightLossProperties())
            .ranked()
            .stream()
            .map(ReasonRanking.Entry::reason)
            .toList();

    assertThat(ranked)
        .extracting(FightReasonDomain::getCode)
        .containsExactly("NO_TP", "ROTATION_WASTED_BEFORE_FIGHT", "NO_VISION_BEFORE_FIGHT");
  }

  @Test
  void aMatchWithNoStoredModeKeepsWhatIsThereAndAddsNothing() {
    analyzer.analyze(MATCH);

    assertThat(reasonRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void deletingRemovesOnlyTheLateRows() {
    stored();
    firstPass();
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(reasonRepo.findByMatchId(MATCH))
        .extracting(FightReasonDomain::getSource)
        .containsExactly("COMMON");
  }

  @Test
  void requiresTheTablesItReads_andRunsAfterEveryAnalyzerThatWritesThem() {
    assertThat(analyzer.requires())
        .containsExactlyInAnyOrder("fights", "vision", "rotations", "fight-reasons");
    int order = AnnotationUtils.findAnnotation(LateFightReasonAnalyzer.class, Order.class).value();
    assertThat(order)
        .isGreaterThan(order(VisionAnalyzer.class))
        .isGreaterThan(order(BehaviourRotationAnalyzer.class))
        .isGreaterThan(order(BehaviourObjectiveAnalyzer.class))
        .isGreaterThan(order(ImportantFightAnalyzer.class))
        .isGreaterThan(order(FightLossAnalyzer.class))
        .isGreaterThan(order(FightReasonAnalyzer.class));
  }

  private static int order(Class<?> type) {
    return AnnotationUtils.findAnnotation(type, Order.class).value();
  }
}
