package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.id.ItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<ItemDomain, ItemId> {

  /**
   * Delete all items associated with a specific match.
   *
   * @param matchId the match ID
   */
  @Modifying
  @Query("DELETE FROM ItemDomain i WHERE i.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);

  List<ItemDomain> findAllByMatchId(@Param("matchId") Long matchId);
}
