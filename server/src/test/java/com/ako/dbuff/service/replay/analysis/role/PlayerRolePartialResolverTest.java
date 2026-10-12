package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
  StoredMatchModes.class,
  GameModeRegistry.class,
  ReplayProcessingProperties.class,
  AllPickProfile.class,
  AbilityDraftProfile.class,
  RoleProfileTestConfig.class,
  PlayerRolePartialResolverTest.Config.class
})
class PlayerRolePartialResolverTest {

  private static final long MATCH = 51L;
  private static final long[] SLOTS = {0, 1, 2, 3, 4, 128, 129, 130, 131, 132};
  private static final long FOREIGN_SLOT = 99;

  @TestConfiguration
  static class Config {
    @Bean
    HeroKits heroKits() {
      return new HeroKits() {
        @Override
        public Optional<com.ako.dbuff.service.constant.data.HeroConstant> hero(long heroId) {
          return Optional.empty();
        }

        @Override
        public Optional<com.ako.dbuff.service.constant.data.AbilityConstant> ability(String name) {
          return Optional.empty();
        }
      };
    }

    /** Gives no role to slots 4 and 132 and one to a slot that is not in the team. */
    @Bean
    RoleResolver partialResolver() {
      return new RoleResolver() {
        @Override
        public boolean supports(GameModeProfile mode) {
          return mode.gameModeId() == 18;
        }

        @Override
        public Set<RoleStage> stages() {
          return Set.of(RoleStage.FINAL);
        }

        @Override
        public List<RoleAssignment> resolve(RoleContext context) {
          List<RoleAssignment> result = new java.util.ArrayList<>();
          List<RolePlayer> team = context.team();
          for (int i = 0; i < 4; i++) {
            result.add(assignment(team.get(i).slot(), i + 1));
          }
          if (team.getFirst().slot() == 0) {
            result.add(assignment(FOREIGN_SLOT, 5));
          }
          return result;
        }

        private RoleAssignment assignment(long slot, int role) {
          return new RoleAssignment(slot, role, PlayerRoleSource.DERIVED, 0.5, "partial");
        }
      };
    }
  }

  @Autowired private PlayerRoleAnalyzer analyzer;
  @Autowired private PlayerRoleRepo roleRepo;
  @Autowired private PlayerRoleProfileRepo profileRepo;
  @Autowired private PlayerGameStatisticRepo statisticRepo;
  @Autowired private EntityManager entityManager;

  @Test
  void slotsTheResolverDidNotCoverOrDoesNotKnowGetNoProfileAndTheRolesAreKept() {
    entityManager.persist(MatchDomain.builder().id(MATCH).gameModeId(18L).build());
    for (long slot : SLOTS) {
      PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
      stat.setMatchId(MATCH);
      stat.setPlayerSlot(slot);
      statisticRepo.save(stat);
    }
    entityManager.flush();

    analyzer.analyze(MATCH);

    assertThat(roleRepo.findByMatchId(MATCH))
        .extracting(PlayerRoleDomain::getPlayerSlot)
        .contains(0L, 1L, 2L, 3L, 128L, 129L, 130L, 131L);
    assertThat(profileRepo.findByMatchId(MATCH))
        .extracting(PlayerRoleProfileDomain::getPlayerSlot)
        .containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 128L, 129L, 130L, 131L);
  }
}
