package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.service.replay.analysis.item.OpenDotaItemPopularity.Phase;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.ws.rs.core.GenericType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenDotaItemPopularityTest {
  private final ApiClient client = mock(ApiClient.class);
  private final RateLimiter limiter = permitting();

  private static RateLimiter permitting() {
    RateLimiter limiter = mock(RateLimiter.class);
    when(limiter.tryAcquire(anyLong(), any(TimeUnit.class))).thenReturn(true);
    return limiter;
  }

  private final OpenDotaItemPopularity source = new OpenDotaItemPopularity(client, limiter);

  private void respond(Map<String, Map<String, Number>> body) throws Exception {
    when(client.<Map<String, Map<String, Number>>>invokeAPI(
            anyString(),
            eq("/heroes/74/itemPopularity"),
            eq("GET"),
            any(),
            any(),
            anyMap(),
            anyMap(),
            anyMap(),
            any(),
            any(),
            any(),
            any(GenericType.class),
            anyBoolean()))
        .thenReturn(new ApiResponse<>(200, Map.of(), body));
  }

  @Test
  void readsTheIdToCountMapsOrderedByCountSkippingNonNumericKeys() throws Exception {
    respond(
        Map.of(
            "start_game_items", Map.of("44", 7, "39", 2),
            "late_game_items", Map.of("1", 3, "2", 9L, "bad", 100, "3", 5.0)));

    Map<Phase, List<Long>> phases = source.fetch(74);

    assertThat(phases.get(Phase.START)).containsExactly(44L, 39L);
    assertThat(phases.get(Phase.LATE)).containsExactly(2L, 3L, 1L);
    assertThat(phases.get(Phase.EARLY)).isEmpty();
    assertThat(phases.get(Phase.MID)).isEmpty();
  }

  @Test
  void aFetchTakesAPermitFromTheSharedLimiterFirst() throws Exception {
    respond(Map.of());

    source.fetch(74);

    verify(limiter).tryAcquire(anyLong(), any(TimeUnit.class));
  }

  @Test
  void noPermitInTimeIsAFailureWithoutACall() {
    when(limiter.tryAcquire(anyLong(), any(TimeUnit.class))).thenReturn(false);

    assertThatThrownBy(() -> source.fetch(74)).isInstanceOf(ApiException.class);
    verifyNoInteractions(client);
  }

  @Test
  void anEmptyBodyGivesEmptyPhases() throws Exception {
    respond(null);

    assertThat(source.fetch(74).values()).allMatch(List::isEmpty);
  }
}
