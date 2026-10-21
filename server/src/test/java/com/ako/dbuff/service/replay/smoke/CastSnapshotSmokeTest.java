package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayCastSnapshotDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayCastSnapshotRepo;
import com.ako.dbuff.service.replay.CastSnapshot;
import com.ako.dbuff.service.replay.CastSnapshotCodec;
import com.ako.dbuff.service.replay.ReplayRunService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the fixtures with the three catalogued skills through the pipeline and counts the casts. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "SMOKE_PG_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@ReplayPipelineTest
class CastSnapshotSmokeTest extends ReplayPipelineFixture {
  private static final int HEROES = 10;

  /** A player whose controller is briefly missing leaves a cast with nine heroes. */
  private static final double MOSTLY = 0.9;

  @Autowired private ReplayRunService runService;
  @Autowired private ReplayCastSnapshotRepo repo;

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void hookShardAndPromiseCastsAreSnapshotted() throws IOException {
    assertThat(castsOf("9040889566", "pudge_meat_hook")).hasSizeGreaterThanOrEqualTo(30);
    assertThat(castsOf("9037038717", "tusk_ice_shards")).hasSizeGreaterThanOrEqualTo(20);
    assertThat(castsOf("9038725154", "oracle_false_promise")).hasSize(10);
  }

  private List<CastSnapshot> castsOf(String id, String ability) throws IOException {
    MatchDomain match = seedMatch(id);
    assertThat(runService.process(match)).isEqualTo(ReplayStatus.DONE);
    ReplayCastSnapshotDomain stored = repo.findByMatchId(match.getId()).orElseThrow();
    List<CastSnapshot> all = CastSnapshotCodec.decode(stored.getData());
    assertThat(all).hasSize(stored.getSnapshotCount());
    List<CastSnapshot> mine = all.stream().filter(s -> s.ability().equals(ability)).toList();
    assertThat(mine).allMatch(s -> s.heroes().size() >= HEROES - 1);
    assertThat(mine.stream().filter(s -> s.heroes().size() == HEROES).count())
        .as("%s: frames with all ten heroes", id)
        .isGreaterThanOrEqualTo((long) (mine.size() * MOSTLY));
    assertThat(mine).allMatch(s -> s.facing() != CastSnapshot.NO_FACING);
    Map<Double, List<CastSnapshot>> byCast =
        mine.stream().collect(Collectors.groupingBy(CastSnapshot::castSecond));
    System.out.println(
        "CASTS "
            + id
            + " "
            + ability
            + ": "
            + byCast.size()
            + " casts, "
            + mine.size()
            + " frames");
    return byCast.values().stream().map(List::getFirst).toList();
  }
}
