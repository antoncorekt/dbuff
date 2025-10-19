package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRepo extends JpaRepository<MatchDomain, Long> {

  List<MatchDomain> findAllByEndProcessIsNull();
}
