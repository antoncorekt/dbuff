package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MatchReportThreadStageTest {

  @Autowired private MatchReportThreadRepo repo;

  @Test
  void theStageAndTheHeaderFactsAreStored_andALegacyRowHasNone() {
    repo.saveAndFlush(
        MatchReportThreadDomain.builder()
            .matchId(1L)
            .instanceId("i")
            .stage(MatchReportStage.REPLAY_PENDING)
            .modeName("ability draft")
            .wins(1)
            .losses(0)
            .durationSeconds(2100L)
            .build());
    repo.saveAndFlush(MatchReportThreadDomain.builder().matchId(2L).instanceId("i").build());

    MatchReportThreadDomain staged = repo.findByMatchIdAndInstanceId(1L, "i").orElseThrow();
    assertThat(staged.getStage()).isEqualTo(MatchReportStage.REPLAY_PENDING);
    assertThat(staged.getModeName()).isEqualTo("ability draft");
    assertThat(staged.getDurationSeconds()).isEqualTo(2100L);
    assertThat(repo.findByMatchIdAndInstanceId(2L, "i").orElseThrow().getStage()).isNull();
  }

  @Test
  void advanceStage_movesOnlyForwardAndTouchesOnlyStageAndFacts() {
    MatchReportThreadDomain saved =
        repo.saveAndFlush(
            MatchReportThreadDomain.builder()
                .matchId(5L)
                .instanceId("i")
                .threadId(77L)
                .stage(MatchReportStage.REQUESTED)
                .build());
    MatchReportStage parsed = MatchReportStage.PARSED;

    int moved =
        repo.advanceStage(saved.getId(), parsed, parsed.predecessors(), "all draft", 1, 0, 2100L);
    int back =
        repo.advanceStage(
            saved.getId(),
            MatchReportStage.REQUESTED,
            MatchReportStage.REQUESTED.predecessors(),
            null,
            null,
            null,
            null);

    MatchReportThreadDomain now = repo.findByMatchIdAndInstanceId(5L, "i").orElseThrow();
    assertThat(moved).isEqualTo(1);
    assertThat(back).isZero();
    assertThat(now.getStage()).isEqualTo(parsed);
    assertThat(now.getModeName()).isEqualTo("all draft");
    assertThat(now.getThreadId()).isEqualTo(77L);
  }

  @Test
  void advanceStage_leavesALegacyRowAlone() {
    MatchReportThreadDomain legacy =
        repo.saveAndFlush(MatchReportThreadDomain.builder().matchId(6L).instanceId("i").build());

    int moved =
        repo.advanceStage(
            legacy.getId(),
            MatchReportStage.PARSED,
            MatchReportStage.PARSED.predecessors(),
            null,
            null,
            null,
            null);

    assertThat(moved).isZero();
  }

  @Test
  void advanceStage_neverErasesStoredFactsWithNulls() {
    MatchReportThreadDomain saved =
        repo.saveAndFlush(
            MatchReportThreadDomain.builder()
                .matchId(8L)
                .instanceId("i")
                .stage(MatchReportStage.REQUESTED)
                .modeName("all draft")
                .wins(1)
                .losses(0)
                .durationSeconds(2100L)
                .build());

    repo.advanceStage(
        saved.getId(),
        MatchReportStage.PARSED,
        MatchReportStage.PARSED.predecessors(),
        null,
        null,
        null,
        null);

    MatchReportThreadDomain now = repo.findByMatchIdAndInstanceId(8L, "i").orElseThrow();
    assertThat(now.getStage()).isEqualTo(MatchReportStage.PARSED);
    assertThat(now.getModeName()).isEqualTo("all draft");
    assertThat(now.getWins()).isEqualTo(1);
    assertThat(now.getLosses()).isZero();
    assertThat(now.getDurationSeconds()).isEqualTo(2100L);
  }

  @Test
  void forceStage_setsTheStageBackwardsAndLeavesALegacyRowAndOtherColumnsAlone() {
    MatchReportThreadDomain staged =
        repo.saveAndFlush(
            MatchReportThreadDomain.builder()
                .matchId(9L)
                .instanceId("i")
                .threadId(77L)
                .stage(MatchReportStage.REPLAY_UNAVAILABLE)
                .modeName("all draft")
                .build());
    MatchReportThreadDomain legacy =
        repo.saveAndFlush(MatchReportThreadDomain.builder().matchId(10L).instanceId("i").build());

    int forced =
        repo.forceStage(staged.getId(), MatchReportStage.REPLAY_PENDING, null, 1, 0, 2100L);
    int untouched =
        repo.forceStage(legacy.getId(), MatchReportStage.PARSED, null, null, null, null);

    MatchReportThreadDomain now = repo.findByMatchIdAndInstanceId(9L, "i").orElseThrow();
    assertThat(forced).isEqualTo(1);
    assertThat(now.getStage()).isEqualTo(MatchReportStage.REPLAY_PENDING);
    assertThat(now.getModeName()).isEqualTo("all draft");
    assertThat(now.getWins()).isEqualTo(1);
    assertThat(now.getThreadId()).isEqualTo(77L);
    assertThat(untouched).isZero();
    assertThat(repo.findByMatchIdAndInstanceId(10L, "i").orElseThrow().getStage()).isNull();
  }

  @Test
  void findAllByMatchId_returnsTheRowsOfEveryInstance() {
    repo.saveAndFlush(MatchReportThreadDomain.builder().matchId(11L).instanceId("a").build());
    repo.saveAndFlush(MatchReportThreadDomain.builder().matchId(11L).instanceId("b").build());
    repo.saveAndFlush(MatchReportThreadDomain.builder().matchId(12L).instanceId("a").build());

    assertThat(repo.findAllByMatchId(11L)).hasSize(2);
  }
}
