package com.ako.dbuff.service.match.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dotapi.api.MatchesApi;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.service.match.data.MatchDetailService;
import com.google.common.util.concurrent.RateLimiter;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchDetailBackfillServiceTest {

  private final MatchRepo matchRepo = mock(MatchRepo.class);
  private final MatchesApi matchesApi = mock(MatchesApi.class);
  private final MatchDetailService detailService = mock(MatchDetailService.class);
  private final MatchDetailBackfillService service =
      new MatchDetailBackfillService(
          matchRepo,
          matchesApi,
          RateLimiter.create(1000),
          detailService,
          new MatchDetailBackfillProperties());

  private final MatchDomain stored = MatchDomain.builder().id(1L).build();
  private final MatchDomain gone = MatchDomain.builder().id(2L).build();
  private final MatchDomain failing = MatchDomain.builder().id(3L).build();
  private final MatchDomain untouched = MatchDomain.builder().id(4L).build();

  @Test
  void backfillBatch_storesFoundMatchesMarksMissingOnesAndStopsOnOtherErrors() throws Exception {
    MatchResponse response = new MatchResponse();
    when(matchRepo.findMatchesWithoutDetails(any()))
        .thenReturn(List.of(stored, gone, failing, untouched));
    when(matchesApi.getMatchesByMatchId(1L)).thenReturn(response);
    when(matchesApi.getMatchesByMatchId(2L)).thenThrow(new ApiException(404, "Not Found"));
    when(matchesApi.getMatchesByMatchId(3L)).thenThrow(new ApiException(429, "Too Many Requests"));

    int completed = service.backfillBatch();

    assertThat(completed).isEqualTo(2);
    verify(detailService).replace(response, stored);
    assertThat(gone.getDetailsMappedAt()).isNotNull();
    assertThat(failing.getDetailsMappedAt()).isNull();
    verify(matchesApi, never()).getMatchesByMatchId(4L);
    verify(matchRepo, never()).save(failing);
    verify(matchRepo, never()).save(untouched);
  }
}
