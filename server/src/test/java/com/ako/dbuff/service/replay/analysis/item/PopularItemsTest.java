package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.analysis.item.OpenDotaItemPopularity.Phase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PopularItemsTest {
  private static final long HERO = 74;
  private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
  private static final Map<String, ItemConstant> ITEMS =
      Map.of(
          "radiance", constant(1, 5_150),
          "maelstrom", constant(2, 2_700),
          "manta", constant(3, 4_600),
          "bfury", constant(4, 4_100),
          "blink", constant(5, 2_250),
          "travel_boots", constant(6, 7_500));

  private final OpenDotaItemPopularity source = mock(OpenDotaItemPopularity.class);
  private final ItemKits kits =
      new ItemKits() {
        @Override
        public Optional<ItemConstant> item(String key) {
          return Optional.ofNullable(ITEMS.get(key));
        }

        @Override
        public Optional<String> key(long id) {
          return ITEMS.entrySet().stream()
              .filter(e -> e.getValue().getId() == id)
              .map(Map.Entry::getKey)
              .findFirst();
        }
      };
  private final MutableClock clock = new MutableClock();
  private final PopularItems popular = new PopularItems(source, kits, clock);

  private static ItemConstant constant(long id, long cost) {
    return ItemConstant.builder().id(id).cost(cost).build();
  }

  private void late(Long... ids) throws ApiException {
    when(source.fetch(HERO)).thenReturn(Map.of(Phase.LATE, List.of(ids), Phase.MID, List.of(5L)));
  }

  @Test
  void namesTheTwoMostPopularOfThePhaseWithinTheCostBandWithoutTheOwnItems() throws Exception {
    late(1L, 6L, 3L, 4L, 2L);

    assertThat(popular.near(HERO, 2_400, 5_150, 0.3, Set.of("radiance")))
        .containsExactly("manta", "bfury");
  }

  @Test
  void thePhaseFollowsThePurchaseMinute() throws Exception {
    late(3L);

    assertThat(popular.near(HERO, 1_000, 2_250, 0.3, Set.of())).containsExactly("blink");
    assertThat(popular.near(HERO, 2_400, 2_250, 0.3, Set.of())).isEmpty();
  }

  @Test
  void theAnswerIsCachedPerHeroForADay() throws Exception {
    late(3L);

    popular.near(HERO, 2_400, 5_150, 0.3, Set.of());
    popular.near(HERO, 2_400, 5_150, 0.3, Set.of());
    verify(source, times(1)).fetch(HERO);

    clock.now = NOW.plus(Duration.ofHours(25));
    popular.near(HERO, 2_400, 5_150, 0.3, Set.of());
    verify(source, times(2)).fetch(HERO);
  }

  @Test
  void aFailureGivesNoFallbackAndIsNotCached() throws Exception {
    when(source.fetch(anyLong())).thenThrow(new ApiException(503, "down"));

    assertThat(popular.near(HERO, 2_400, 5_150, 0.3, Set.of())).isEmpty();
    assertThat(popular.near(HERO, 2_400, 5_150, 0.3, Set.of())).isEmpty();
    verify(source, times(2)).fetch(HERO);
  }

  private static final class MutableClock extends Clock {
    Instant now = NOW;

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
