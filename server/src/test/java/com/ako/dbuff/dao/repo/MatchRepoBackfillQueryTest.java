package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MatchRepoBackfillQueryTest {

  private static final LocalDateTime NOW = LocalDateTime.now();
  private static final LocalDateTime RETRY_BEFORE = NOW.minusHours(24);
  private static final long RECENT_AFTER = 1_000L;

  @Autowired private MatchRepo matchRepo;
  @Autowired private EntityManager entityManager;

  @Test
  void findMatchesWithoutDetails_returnsProcessedUnmappedMatchesNewestFirstEvenAfterAnApiFailure() {
    save(1L, 1_100L, NOW, null, null, null);
    save(2L, 1_300L, NOW, null, null, false);
    save(3L, 1_200L, NOW, NOW, null, null);
    save(4L, 1_400L, null, null, null, null);
    save(5L, 1_500L, NOW, null, null, true);

    assertThat(ids(PageRequest.of(0, 10))).containsExactly(5L, 2L, 1L);
  }

  @Test
  void findMatchesWithoutDetails_honoursThePageSize() {
    save(1L, 1_100L, NOW, null, null, null);
    save(2L, 1_200L, NOW, null, null, null);

    assertThat(ids(PageRequest.of(0, 1))).containsExactly(2L);
  }

  @Test
  void findMatchesWithoutDetails_retriesATriedMatchOnlyAfterTheCooldownAndWhileItIsRecent() {
    LocalDateTime longAgo = NOW.minusDays(2);
    save(1L, 1_100L, NOW, null, NOW.minusHours(1), null);
    save(2L, 1_200L, NOW, null, longAgo, null);
    save(3L, 500L, NOW, null, longAgo, null);

    assertThat(ids(PageRequest.of(0, 10))).containsExactly(2L);
  }

  @Test
  void updateDetailState_changesOnlyTheBookkeepingColumns() {
    MatchDomain match = save(1L, 1_100L, NOW, null, null, null);
    match.setError("set by another job");
    matchRepo.saveAndFlush(match);

    matchRepo.updateDetailState(1L, true, 7L, 8L, NOW, NOW);
    entityManager.clear();

    MatchDomain reloaded = matchRepo.findAll().getFirst();
    assertThat(reloaded.getHasParsed()).isTrue();
    assertThat(reloaded.getReplaySalt()).isEqualTo(8L);
    assertThat(reloaded.getDetailsMappedAt()).isNotNull();
    assertThat(reloaded.getError()).isEqualTo("set by another job");
  }

  private List<Long> ids(PageRequest page) {
    return matchRepo.findMatchesWithoutDetails(RETRY_BEFORE, RECENT_AFTER, page).stream()
        .map(MatchDomain::getId)
        .toList();
  }

  private MatchDomain save(
      long id,
      long startTime,
      LocalDateTime end,
      LocalDateTime mapped,
      LocalDateTime attempted,
      Boolean apiFailed) {
    return matchRepo.saveAndFlush(
        MatchDomain.builder()
            .id(id)
            .startTime(startTime)
            .endProcess(end)
            .detailsMappedAt(mapped)
            .detailsAttemptedAt(attempted)
            .dotaApiFailed(apiFailed)
            .build());
  }
}
