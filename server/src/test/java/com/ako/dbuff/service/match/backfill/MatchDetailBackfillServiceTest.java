package com.ako.dbuff.service.match.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.service.match.DotaApiMatchDetailsService;
import com.ako.dbuff.service.match.data.MatchDetailService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchDetailBackfillServiceTest {

  private final MatchRepo matchRepo = mock(MatchRepo.class);
  private final DotaApiMatchDetailsService api = mock(DotaApiMatchDetailsService.class);
  private final MatchDetailService detailService = mock(MatchDetailService.class);
  private final MatchDetailBackfillService service =
      new MatchDetailBackfillService(
          matchRepo, api, detailService, new MatchDetailBackfillProperties());

  private final MatchResponse response = new MatchResponse();

  @Test
  void backfillBatch_storesParsedMatchesThroughTheBookkeepingUpdateOnly() throws Exception {
    MatchDomain match = MatchDomain.builder().id(1L).build();
    pending(match);
    when(api.fetchOnce(1L)).thenReturn(response);
    doAnswer(
            call -> {
              match.setHasParsed(true);
              match.setDetailsMappedAt(LocalDateTime.now());
              return null;
            })
        .when(detailService)
        .replace(response, match);

    int stored = service.backfillBatch();

    assertThat(stored).isEqualTo(1);
    verify(matchRepo).updateDetailState(eq(1L), eq(true), any(), any(), any(), any());
    verify(matchRepo, never()).save(any());
  }

  @Test
  void backfillBatch_countsAnUnparsedMatchAsTriedButNotStored() throws Exception {
    MatchDomain match = MatchDomain.builder().id(1L).build();
    pending(match);
    when(api.fetchOnce(1L)).thenReturn(response);

    int stored = service.backfillBatch();

    assertThat(stored).isZero();
    verify(matchRepo).updateDetailState(eq(1L), any(), any(), any(), isNull(), any());
  }

  @Test
  void backfillBatch_skipsAMatchOpenDotaRejectsAndCarriesOn() throws Exception {
    MatchDomain missing = MatchDomain.builder().id(1L).build();
    MatchDomain next = MatchDomain.builder().id(2L).build();
    pending(missing, next);
    when(api.fetchOnce(1L)).thenThrow(new ApiException(404, "Not Found"));
    when(api.fetchOnce(2L)).thenReturn(response);

    service.backfillBatch();

    assertThat(missing.getDetailsAttemptedAt()).isNotNull();
    verify(matchRepo).updateDetailState(eq(1L), any(), any(), any(), isNull(), any());
    verify(detailService).replace(response, next);
  }

  @Test
  void backfillBatch_skipsAMatchWhoseRowsCannotBeStoredAndCarriesOn() throws Exception {
    MatchDomain broken = MatchDomain.builder().id(1L).build();
    MatchDomain next = MatchDomain.builder().id(2L).build();
    pending(broken, next);
    when(api.fetchOnce(any(Long.class))).thenReturn(response);
    doThrow(new IllegalStateException("bad shape")).when(detailService).replace(response, broken);

    service.backfillBatch();

    assertThat(broken.getDetailsAttemptedAt()).isNotNull();
    verify(detailService).replace(response, next);
  }

  @Test
  void backfillBatch_stopsTheRunOnARateLimitWithoutMarkingTheMatch() throws Exception {
    MatchDomain limited = MatchDomain.builder().id(1L).build();
    MatchDomain untouched = MatchDomain.builder().id(2L).build();
    pending(limited, untouched);
    when(api.fetchOnce(1L)).thenThrow(new ApiException(429, "Too Many Requests"));

    service.backfillBatch();

    assertThat(limited.getDetailsAttemptedAt()).isNull();
    verify(api, never()).fetchOnce(2L);
    verify(matchRepo, never()).updateDetailState(any(), any(), any(), any(), any(), any());
  }

  private void pending(MatchDomain... matches) {
    when(matchRepo.findMatchesWithoutDetails(any(), any(), any())).thenReturn(List.of(matches));
  }
}
