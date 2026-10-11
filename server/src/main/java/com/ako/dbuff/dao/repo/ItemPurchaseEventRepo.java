package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ItemPurchaseEventDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ItemPurchaseEventRepo extends JpaRepository<ItemPurchaseEventDomain, Long> {
  @Transactional
  @Modifying
  @Query("DELETE FROM ItemPurchaseEventDomain e WHERE e.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
