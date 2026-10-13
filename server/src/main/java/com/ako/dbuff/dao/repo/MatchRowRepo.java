package com.ako.dbuff.dao.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** A repository of rows that belong to one match and are removed with it. */
@NoRepositoryBean
public interface MatchRowRepo<T> extends JpaRepository<T, Long> {
  void deleteByMatchId(Long matchId);
}
