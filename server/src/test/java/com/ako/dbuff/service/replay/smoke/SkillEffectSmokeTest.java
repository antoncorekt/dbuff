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

/**
 * Runs the fixtures where Sniper has False Promise, Pudge has Meat Hook and Doom has Ice Shards and
 * checks the rows.
 */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "SMOKE_PG_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@ReplayPipelineTest
class SkillEffectSmokeTest extends ReplayPipelineFixture {
  private static final String PROMISE = "false_promise";
  private static final long SNIPER_SLOT = 0;
  private static final int CASTS = 10;
  private static final String HOOK = "meat_hook";
  private static final long PUDGE_SLOT = 131;
  private static final long RUBICK_SLOT = 1;
  private static final int PUDGE_MIN_CASTS = 30;
  private static final int RUBICK_CASTS = 2;
  private static final String SHARDS = "ice_shards";
  private static final long DOOM_SLOT = 131;
  private static final int DOOM_MIN_CASTS = 20;

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

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void pudgeHooksAreJudgedAndRubickHasHisTwo() throws IOException {
    try {
      MatchDomain match = seedMatch("9040889566");
      assertThat(runService.process(match)).isEqualTo(ReplayStatus.DONE);

      SkillEffectDomain pudge = hookRow(match, PUDGE_SLOT);
      JsonNode figures = new ObjectMapper().readTree(pudge.getFigures());

      System.out.println("SKILL EFFECTS 9040889566 pudge: " + pudge.getFigures());
      assertThat(pudge.getCastCount()).isGreaterThanOrEqualTo(PUDGE_MIN_CASTS);
      assertThat(figures.get("SEEN_HIT").asInt() + figures.get("DARK_HIT").asInt())
          .isGreaterThanOrEqualTo(1);
      for (JsonNode row : new ObjectMapper().readTree(pudge.getDetails())) {
        boolean noFrameDarkHit =
            "DARK_HIT".equals(row.get("outcome").asText()) && row.get("numbers").has("noSnapshot");
        assertThat(noFrameDarkHit)
            .as("dark hit without a frame at %s", row.get("second"))
            .isFalse();
      }
      assertThat(hookRow(match, RUBICK_SLOT).getCastCount()).isEqualTo(RUBICK_CASTS);
    } finally {
      runRepo.deleteAll();
    }
  }

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void doomShardsAreJudged() throws IOException {
    try {
      MatchDomain match = seedMatch("9037038717");
      assertThat(runService.process(match)).isEqualTo(ReplayStatus.DONE);

      SkillEffectDomain doom =
          repo.findByMatchId(match.getId()).stream()
              .filter(r -> r.getPlayerSlot() == DOOM_SLOT && SHARDS.equals(r.getEntryKey()))
              .findFirst()
              .orElseThrow();
      JsonNode figures = new ObjectMapper().readTree(doom.getFigures());

      System.out.println("SKILL EFFECTS 9037038717 doom: " + doom.getFigures());
      assertThat(doom.getCastCount()).isGreaterThanOrEqualTo(DOOM_MIN_CASTS);
      assertThat(figures.get("TRAP").asInt() + figures.get("MISS").asInt())
          .isGreaterThanOrEqualTo(1);
    } finally {
      runRepo.deleteAll();
    }
  }

  private SkillEffectDomain hookRow(MatchDomain match, long slot) {
    return repo.findByMatchId(match.getId()).stream()
        .filter(r -> r.getPlayerSlot() == slot && HOOK.equals(r.getEntryKey()))
        .findFirst()
        .orElseThrow();
  }
}
