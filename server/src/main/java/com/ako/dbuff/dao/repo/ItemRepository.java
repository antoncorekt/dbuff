package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.id.ItemId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ItemRepository extends JpaRepository<ItemDomain, ItemId> {

  /**
   * Delete all items associated with a specific match.
   *
   * @param matchId the match ID
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM ItemDomain i WHERE i.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);

  List<ItemDomain> findAllByMatchId(@Param("matchId") Long matchId);
}
