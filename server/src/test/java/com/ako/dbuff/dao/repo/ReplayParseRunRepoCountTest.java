package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReplayParseRunRepoCountTest {

  @Autowired private ReplayParseRunRepo runRepo;

  private void run(long matchId, int version, ReplayStatus status) {
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(matchId)
            .parserVersion(version)
            .status(status)
            .attempts(1)
            .build());
  }

  @Test
  void countByStatus_groupsTheRunsOfOneParserVersion() {
    run(1, 1, ReplayStatus.DONE);
    run(2, 1, ReplayStatus.DONE);
    run(3, 1, ReplayStatus.PARTIAL);
    run(4, 1, ReplayStatus.FAILED_DOWNLOAD);
    run(5, 2, ReplayStatus.DONE);

    Map<ReplayStatus, Long> counts =
        runRepo.countByStatus(1).stream()
            .collect(
                Collectors.toMap(
                    ReplayParseRunRepo.StatusCount::getStatus,
                    ReplayParseRunRepo.StatusCount::getTotal));

    assertThat(counts)
        .containsOnly(
            Map.entry(ReplayStatus.DONE, 2L),
            Map.entry(ReplayStatus.PARTIAL, 1L),
            Map.entry(ReplayStatus.FAILED_DOWNLOAD, 1L));
  }
}
