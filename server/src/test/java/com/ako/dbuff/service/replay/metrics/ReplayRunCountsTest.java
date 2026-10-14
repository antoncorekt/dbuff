package com.ako.dbuff.service.replay.metrics;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo.StatusCount;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayRunCountsTest {

  private final ReplayParseRunRepo repo = mock(ReplayParseRunRepo.class);
  private final MutableClock clock = new MutableClock();
  private final ReplayRunCounts counts =
      new ReplayRunCounts(repo, new ReplayProcessingProperties(), clock);

  private static StatusCount row(ReplayStatus status, long total) {
    return new StatusCount() {
      @Override
      public ReplayStatus getStatus() {
        return status;
      }

      @Override
      public Long getTotal() {
        return total;
      }
    };
  }

  @Test
  void countOf_readsTheGroupedRowsAndZeroForAStatusWithNoRuns() {
    when(repo.countByStatus(any())).thenReturn(List.of(row(ReplayStatus.DONE, 7)));

    assertThat(counts.countOf(ReplayStatus.DONE)).isEqualTo(7);
    assertThat(counts.countOf(ReplayStatus.FAILED_PARSER)).isZero();
  }

  @Test
  void countOf_asksTheDatabaseOncePerThirtySeconds() {
    when(repo.countByStatus(any())).thenReturn(List.of(row(ReplayStatus.DONE, 1)));

    counts.countOf(ReplayStatus.DONE);
    clock.advanceSeconds(29);
    counts.countOf(ReplayStatus.PARTIAL);
    verify(repo, times(1)).countByStatus(any());

    when(repo.countByStatus(any())).thenReturn(List.of(row(ReplayStatus.DONE, 2)));
    clock.advanceSeconds(1);
    assertThat(counts.countOf(ReplayStatus.DONE)).isEqualTo(2);
    verify(repo, times(2)).countByStatus(any());
  }

  @Test
  void countOf_keepsTheLastCountsWhenTheDatabaseFails() {
    when(repo.countByStatus(any())).thenReturn(List.of(row(ReplayStatus.DONE, 3)));
    counts.countOf(ReplayStatus.DONE);
    clock.advanceSeconds(31);
    when(repo.countByStatus(any())).thenThrow(new IllegalStateException("db down"));

    assertThat(counts.countOf(ReplayStatus.DONE)).isEqualTo(3);
  }

  @Test
  void countOf_retriesOnTheNextCallWhenTheFirstReadFailed() {
    when(repo.countByStatus(any())).thenThrow(new IllegalStateException("db down"));
    assertThat(counts.countOf(ReplayStatus.DONE)).isZero();

    doReturn(List.of(row(ReplayStatus.DONE, 4))).when(repo).countByStatus(any());

    assertThat(counts.countOf(ReplayStatus.DONE)).isEqualTo(4);
  }

  @Test
  void countOf_servesTheStaleCountsWhileAnotherCallerIsRefreshing() throws Exception {
    when(repo.countByStatus(any())).thenReturn(List.of(row(ReplayStatus.DONE, 1)));
    counts.countOf(ReplayStatus.DONE);
    clock.advanceSeconds(31);
    CountDownLatch inQuery = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(repo.countByStatus(any()))
        .thenAnswer(
            call -> {
              inQuery.countDown();
              release.await();
              return List.of(row(ReplayStatus.DONE, 2));
            });
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<Long> refresher = pool.submit(() -> counts.countOf(ReplayStatus.DONE));
      inQuery.await();

      assertThat(counts.countOf(ReplayStatus.DONE)).isEqualTo(1);

      release.countDown();
      assertThat(refresher.get()).isEqualTo(2);
      assertThat(counts.countOf(ReplayStatus.DONE)).isEqualTo(2);
    } finally {
      pool.shutdownNow();
    }
  }

  private static class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-14T10:00:00Z");

    void advanceSeconds(long seconds) {
      now = now.plusSeconds(seconds);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
