package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.model.SkillEffectDomain;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.SkillEffectRepo;
import com.ako.dbuff.service.replay.ReplayRunService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the Ability Draft fixture where Sniper has False Promise and checks its skill effects. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "SMOKE_PG_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@ReplayPipelineTest
class SkillEffectSmokeTest extends ReplayPipelineFixture {
  private static final String PROMISE = "false_promise";
  private static final long SNIPER_SLOT = 0;
  private static final int CASTS = 10;

  @Autowired private ReplayRunService runService;
  @Autowired private SkillEffectRepo repo;
  @Autowired private ReplayParseRunRepo runRepo;

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void sniperPromisesAreAllJudged() throws IOException {
    try {
      MatchDomain match = seedMatch("9038725154");
      assertThat(runService.process(match)).isEqualTo(ReplayStatus.DONE);

      SkillEffectDomain row =
          repo.findByMatchId(match.getId()).stream()
              .filter(r -> r.getPlayerSlot() == SNIPER_SLOT && PROMISE.equals(r.getEntryKey()))
              .findFirst()
              .orElseThrow();
      JsonNode figures = new ObjectMapper().readTree(row.getFigures());
      int judged =
          figures.get("SAVE").asInt()
              + figures.get("IMPACT").asInt()
              + figures.get("NOT_COUNTED").asInt();

      System.out.println("SKILL EFFECTS 9038725154 sniper: " + row.getFigures());
      assertThat(row.getCastCount()).isEqualTo(CASTS);
      assertThat(judged).isEqualTo(CASTS);
    } finally {
      // ReplayLoadTest needs an empty replay_parse_run and may run after this class.
      runRepo.deleteAll();
    }
  }
}
