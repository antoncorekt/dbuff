package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRepo extends JpaRepository<MatchDomain, Long> {

  List<MatchDomain> findAllByEndProcessIsNull();

  @Query(
      """
      select m from MatchDomain m
      where m.detailsMappedAt is null
        and m.endProcess is not null
        and (m.dotaApiFailed is null or m.dotaApiFailed = false)
      order by m.startTime desc
      """)
  List<MatchDomain> findMatchesWithoutDetails(Pageable pageable);

  @QueryHints(value = @QueryHint(name = "org.hibernate.fetchSize", value = "100"))
  @Query("select o from MatchDomain o")
  Stream<MatchDomain> findAllStream();
}
