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
}
