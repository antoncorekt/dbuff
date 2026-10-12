package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
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
  PlayerRoleAnalyzer.class,
  AllPickRoleResolver.class,
  StoredMatchModes.class,
  GameModeRegistry.class,
  ReplayProcessingProperties.class,
  ReplayAnalysisProperties.class,
  AllPickProfile.class,
  AbilityDraftProfile.class
})
class PlayerRoleAnalyzerTest {

  private static final long ALL_PICK_MATCH = 31L;
  private static final long ABILITY_DRAFT_MATCH = 32L;
  private static final long[] SLOTS = {0, 1, 2, 3, 4, 128, 129, 130, 131, 132};
  private static final long[] LANE_ROLES = {1, 2, 3, 3, 1};
  private static final long[] WORTH = {24000, 18000, 13000, 7000, 5000};

  @Autowired private PlayerRoleAnalyzer analyzer;
  @Autowired private PlayerRoleRepo roleRepo;
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
              assertThat(r.getAnalysisVersion()).isEqualTo(1);
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
  }

  @Test
  void anAbilityDraftMatchGetsNoRoles() {
    stored(ABILITY_DRAFT_MATCH, 18);

    assertThat(analyzer.supports(TestModes.ABILITY_DRAFT)).isFalse();
    analyzer.analyze(ABILITY_DRAFT_MATCH);

    assertThat(roleRepo.findByMatchId(ABILITY_DRAFT_MATCH)).isEmpty();
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
  }
}
