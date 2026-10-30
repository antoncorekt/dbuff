package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayRunService;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** A passive skill never gets a fight ability row, so it is never "ready and not cast". */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "SMOKE_PG_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@ReplayPipelineTest
class PassiveSkillReplayTest extends ReplayPipelineFixture {
  private static final String MATCH = "9058566396";
  private static final List<String> PASSIVES =
      List.of(
          "axe_counter_helix", "pangolier_lucky_shot", "ursa_fury_swipes", "troll_warlord_fervor");
  private static final String CASTABLE = "axe_culling_blade";

  @Autowired private ReplayRunService runService;
  @Autowired private FightAbilityUseRepo useRepo;
  @Autowired private ReplayParseRunRepo runRepo;

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void passivesGetNoFightAbilityRowAndCullingBladeStillDoes() throws IOException {
    try {
      MatchDomain match = seedMatch(MATCH);
      assertThat(runService.process(match)).isEqualTo(ReplayStatus.DONE);

      List<String> names =
          useRepo.findByMatchId(match.getId()).stream()
              .map(FightAbilityUseDomain::getAbilityName)
              .toList();

      assertThat(names).doesNotContainAnyElementsOf(PASSIVES);
      assertThat(names).contains(CASTABLE);
    } finally {
      runRepo.deleteAll();
    }
  }
}
