package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDefenseTrackDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.dao.repo.PlayerDefenseTrackRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.TeamDefensePointRepo;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.ReplayRunService;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimelines;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("replay")
@EnabledIfEnvironmentVariable(named = "SMOKE_PG_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@ReplayPipelineTest
class TeamDefenseReplayTest extends ReplayPipelineFixture {
  private static final String MATCH = "9044278208";
  private static final String SHIVAS = "item_shivas_guard";
  private static final int SHIVAS_END_ARMOR_X10 = 190;

  @Autowired private ReplayRunService runService;
  @Autowired private PlayerDefenseTrackRepo trackRepo;
  @Autowired private TeamDefensePointRepo pointRepo;
  @Autowired private HeroStateTimelines stateTimelines;
  @Autowired private ReplayParseRunRepo runRepo;

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void storesDefenseValuesForEveryHeroAndMinute() throws IOException {
    try {
      MatchDomain match = seedMatch(MATCH);
      assertThat(runService.process(match)).isEqualTo(ReplayStatus.DONE);

      List<PlayerDefenseTrackDomain> tracks = trackRepo.findByMatchId(match.getId());
      assertThat(tracks).hasSize(10);

      List<TeamDefensePointDomain> points = pointRepo.findByMatchId(match.getId());
      assertThat(points)
          .filteredOn(p -> p.getCheckpoint() == null || !p.getCheckpoint().equals("END"))
          .filteredOn(p -> p.getMinute() >= 1)
          .allSatisfy(
              p -> {
                assertThat(p.getEhpPhys()).isNotNull();
                assertThat(p.getEhpMix()).isNotNull();
              });

      Map<Integer, HeroStateTimeline> timelines = stateTimelines.load(match.getId());
      long holder =
          timelines.entrySet().stream()
              .filter(e -> e.getValue().itemsAt(Double.MAX_VALUE).contains(SHIVAS))
              .map(e -> PlayerSlots.slot(e.getKey()))
              .findFirst()
              .orElseThrow();
      int[] armor =
          tracks.stream()
              .filter(t -> t.getPlayerSlot() == holder)
              .findFirst()
              .orElseThrow()
              .getArmorX10();
      assertThat(armor[armor.length - 1]).isGreaterThanOrEqualTo(SHIVAS_END_ARMOR_X10);
    } finally {
      runRepo.deleteAll();
    }
  }
}
