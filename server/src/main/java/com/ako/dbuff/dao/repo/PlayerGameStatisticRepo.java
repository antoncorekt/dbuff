package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.id.PlayerGameStatisticDomainId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerGameStatisticRepo
    extends JpaRepository<PlayerMatchStatisticDomain, PlayerGameStatisticDomainId> {

  List<PlayerMatchStatisticDomain> findAllByMatchId(long matchId);
}
