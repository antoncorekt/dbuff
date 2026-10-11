package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({ReplayRunJanitor.class, ReplayProcessingProperties.class})
class ReplayRunJanitorTest {

  private static final LocalDateTime NOW = LocalDateTime.now();

  @Autowired private ReplayRunJanitor janitor;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private ReplayProcessingProperties properties;

  @Test
  void closeAbandoned_failsOnlyRunsThatDiedOnTheirLastAttempt() {
    run(1L, properties.getMaxAttempts(), NOW.minusMinutes(5));
    run(2L, properties.getMaxAttempts() - 1, NOW.minusMinutes(5));
    run(3L, properties.getMaxAttempts(), NOW.plusMinutes(5));

    janitor.closeAbandoned();

    assertThat(runRepo.findByMatchIdAndParserVersion(1L, properties.getParserVersion()))
        .get()
        .satisfies(
            r -> {
              assertThat(r.getStatus()).isEqualTo(ReplayStatus.FAILED_RESOURCE);
              assertThat(r.getLeaseUntil()).isNull();
              assertThat(r.getNextAttemptAt()).isNull();
            });
    assertThat(runRepo.findByMatchIdAndParserVersion(2L, properties.getParserVersion()))
        .get()
        .extracting(ReplayParseRunDomain::getStatus)
        .isEqualTo(ReplayStatus.RUNNING);
    assertThat(runRepo.findByMatchIdAndParserVersion(3L, properties.getParserVersion()))
        .get()
        .extracting(ReplayParseRunDomain::getStatus)
        .isEqualTo(ReplayStatus.RUNNING);
  }

  private void run(long matchId, int attempts, LocalDateTime leaseUntil) {
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(matchId)
            .parserVersion(properties.getParserVersion())
            .status(ReplayStatus.RUNNING)
            .attempts(attempts)
            .leaseUntil(leaseUntil)
            .build());
  }
}
