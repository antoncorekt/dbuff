package com.ako.dbuff.dao.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ako.dbuff.dao.model.MatchDomain;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class MatchRepoBackfillQueryTest {

  @Autowired private MatchRepo matchRepo;

  @Test
  void findMatchesWithoutDetails_returnsProcessedUnmappedMatchesNewestFirst() {
    LocalDateTime done = LocalDateTime.now();
    save(1L, 100L, done, null, null);
    save(2L, 300L, done, null, false);
    save(3L, 200L, done, done, null);
    save(4L, 400L, null, null, null);
    save(5L, 500L, done, null, true);

    List<MatchDomain> found = matchRepo.findMatchesWithoutDetails(PageRequest.of(0, 10));

    assertThat(found).extracting(MatchDomain::getId).containsExactly(2L, 1L);
  }

  @Test
  void findMatchesWithoutDetails_honoursThePageSize() {
    LocalDateTime done = LocalDateTime.now();
    save(1L, 100L, done, null, null);
    save(2L, 200L, done, null, null);

    assertThat(matchRepo.findMatchesWithoutDetails(PageRequest.of(0, 1)))
        .extracting(MatchDomain::getId)
        .containsExactly(2L);
  }

  private void save(
      long id, long startTime, LocalDateTime end, LocalDateTime mapped, Boolean apiFailed) {
    matchRepo.save(
        MatchDomain.builder()
            .id(id)
            .startTime(startTime)
            .endProcess(end)
            .detailsMappedAt(mapped)
            .dotaApiFailed(apiFailed)
            .build());
  }
}
