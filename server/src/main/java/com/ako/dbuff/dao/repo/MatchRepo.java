package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRepo extends JpaRepository<MatchDomain, Long> {

  List<MatchDomain> findAllByEndProcessIsNull();

  @QueryHints(value = @QueryHint(name = "org.hibernate.fetchSize", value = "100"))
  @Query("select o from MatchDomain o")
  Stream<MatchDomain> findAllStream();
}
