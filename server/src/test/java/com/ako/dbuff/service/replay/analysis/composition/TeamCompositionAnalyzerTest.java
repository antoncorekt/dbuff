package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.mode.TestModes;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  TeamCompositionAnalyzer.class,
  DefaultCompositionRules.class,
  CompositionProperties.class,
  StoredMatchModes.class,
  GameModeRegistry.class,
  ReplayProcessingProperties.class,
  AllPickProfile.class,
  AbilityDraftProfile.class
})
class TeamCompositionAnalyzerTest {
  private static final long ALL_PICK_MATCH = 41L;
  private static final long ABILITY_DRAFT_MATCH = 42L;
  private static final long OTHER_MODE_MATCH = 43L;
  private static final long[] SLOTS = {0, 1, 2, 3, 4, 128, 129, 130, 131, 132};

  @Autowired private TeamCompositionAnalyzer analyzer;
  @Autowired private TeamCompositionRepo compositionRepo;
  @Autowired private PlayerRoleProfileRepo profileRepo;
  @Autowired private EntityManager entityManager;

  private void match(long matchId, long mode) {
    entityManager.persist(MatchDomain.builder().id(matchId).gameModeId(mode).build());
    entityManager.flush();
  }

  private void profiles(long matchId, String stage, double farmOfFirst, int rotation) {
    for (int i = 0; i < SLOTS.length; i++) {
      int index = i % 5;
      profileRepo.save(
          PlayerRoleProfileDomain.builder()
              .matchId(matchId)
              .playerSlot(SLOTS[i])
              .stage(stage)
              .role((index + rotation) % 5 + 1)
              .farmPriority(index == 0 ? farmOfFirst : 0.2)
              .build());
    }
    entityManager.flush();
  }

  private TeamCompositionDomain row(long matchId, String team, String stage) {
    return compositionRepo.findByMatchId(matchId).stream()
        .filter(c -> c.getTeam().equals(team) && c.getStage().equals(stage))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aMatchWithIntentAndFinalGetsAPickAndAFinalRowPerTeam() {
    match(ABILITY_DRAFT_MATCH, 18);
    profiles(ABILITY_DRAFT_MATCH, "INTENT", 0.9, 0);
    profiles(ABILITY_DRAFT_MATCH, "FINAL", 0.9, 2);

    analyzer.analyze(ABILITY_DRAFT_MATCH);

    List<TeamCompositionDomain> rows = compositionRepo.findByMatchId(ABILITY_DRAFT_MATCH);
    assertThat(rows)
        .extracting(c -> c.getTeam() + "/" + c.getStage())
        .containsExactlyInAnyOrder("RADIANT/PICK", "RADIANT/FINAL", "DIRE/PICK", "DIRE/FINAL");
    assertThat(rows).allSatisfy(r -> assertThat(r.getAnalysisVersion()).isEqualTo(2));
    assertThat(row(ABILITY_DRAFT_MATCH, "RADIANT", "PICK").getTokens())
        .doesNotContain("INTENT_NOT_PLAYED");
    TeamCompositionDomain played = row(ABILITY_DRAFT_MATCH, "DIRE", "FINAL");
    assertThat(played.getTokens().split(",")).contains("INTENT_NOT_PLAYED");
    assertThat(played.getSummary()).contains("played other roles").contains("Unknown:");
  }

  @Test
  void aFinalOnlyModeWritesFinalRowsAndNoIntentToken() {
    match(ALL_PICK_MATCH, 22);
    profiles(ALL_PICK_MATCH, "FINAL", 0.9, 0);

    analyzer.analyze(ALL_PICK_MATCH);

    List<TeamCompositionDomain> rows = compositionRepo.findByMatchId(ALL_PICK_MATCH);
    assertThat(rows).extracting(TeamCompositionDomain::getStage).containsOnly("FINAL");
    assertThat(rows).hasSize(2);
    assertThat(rows.getFirst().getTokens()).doesNotContain("INTENT_NOT_PLAYED");
    assertThat(rows.getFirst().getSummary()).contains("Unknown: ");
  }

  @Test
  void aListIsCutAtATokenAndASentenceGetsAnEllipsis() {
    assertThat(TeamCompositionAnalyzer.fit("NO_A,NO_B,NO_C", 10, ",")).isEqualTo("NO_A,NO_B");
    assertThat(TeamCompositionAnalyzer.fit("NO_A,NO_B", 20, ",")).isEqualTo("NO_A,NO_B");
    assertThat(TeamCompositionAnalyzer.fit("abcdefghij", 8, "")).isEqualTo("abcde...");
    assertThat(TeamCompositionAnalyzer.fit("abcdefgh", 8, "")).isEqualTo("abcdefgh");
  }

  @Test
  void aSecondRunReplacesTheFirst() {
    match(ALL_PICK_MATCH, 22);
    profiles(ALL_PICK_MATCH, "FINAL", 0.9, 0);

    analyzer.analyze(ALL_PICK_MATCH);
    analyzer.analyze(ALL_PICK_MATCH);

    assertThat(compositionRepo.findByMatchId(ALL_PICK_MATCH)).hasSize(2);
  }

  @Test
  void aRerunAfterTheProfilesChangedStoresTheNewVerdict() {
    match(ALL_PICK_MATCH, 22);
    profiles(ALL_PICK_MATCH, "FINAL", 0.9, 0);
    analyzer.analyze(ALL_PICK_MATCH);
    assertThat(row(ALL_PICK_MATCH, "RADIANT", "FINAL").getTokens())
        .doesNotContain("NO_FARM_ENGINE");

    profileRepo.deleteByMatchId(ALL_PICK_MATCH);
    entityManager.clear();
    profiles(ALL_PICK_MATCH, "FINAL", 0.2, 0);
    analyzer.analyze(ALL_PICK_MATCH);

    assertThat(row(ALL_PICK_MATCH, "RADIANT", "FINAL").getTokens()).contains("NO_FARM_ENGINE");
  }

  @Test
  void aTeamWithoutFiveProfilesGetsNoRowForThatStage() {
    match(ALL_PICK_MATCH, 22);
    profiles(ALL_PICK_MATCH, "FINAL", 0.9, 0);
    profileRepo.deleteAll(
        profileRepo.findByMatchId(ALL_PICK_MATCH).stream()
            .filter(p -> p.getPlayerSlot() == 132)
            .toList());
    entityManager.flush();

    analyzer.analyze(ALL_PICK_MATCH);

    assertThat(compositionRepo.findByMatchId(ALL_PICK_MATCH))
        .extracting(TeamCompositionDomain::getTeam)
        .containsExactly("RADIANT");
  }

  @Test
  void aModeWithoutRulesWritesNothing() {
    match(OTHER_MODE_MATCH, 1);
    profiles(OTHER_MODE_MATCH, "FINAL", 0.9, 0);

    analyzer.analyze(OTHER_MODE_MATCH);

    assertThat(analyzer.supports(TestModes.ALL_PICK)).isTrue();
    assertThat(compositionRepo.findByMatchId(OTHER_MODE_MATCH)).isEmpty();
  }

  @Test
  void deleteRemovesTheRowsAndTheAnalyzerWaitsForTheRoles() {
    match(ALL_PICK_MATCH, 22);
    profiles(ALL_PICK_MATCH, "FINAL", 0.9, 0);
    analyzer.analyze(ALL_PICK_MATCH);

    analyzer.deleteByMatchId(ALL_PICK_MATCH);

    assertThat(compositionRepo.findByMatchId(ALL_PICK_MATCH)).isEmpty();
    assertThat(analyzer.requires()).containsExactly("roles");
  }
}
