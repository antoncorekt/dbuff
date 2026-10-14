package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FightAbilityUseRepo extends JpaRepository<FightAbilityUseDomain, Long> {

  List<FightAbilityUseDomain> findByMatchId(Long matchId);

  @Query(
      "select distinct d.abilityName from FightAbilityUseDomain d where d.abilityName is not null")
  List<String> findDistinctAbilityNames();

  @Transactional
  @Modifying
  @Query("delete from FightAbilityUseDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
