package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SkillPickStatRepo extends JpaRepository<SkillPickStatDomain, Long> {

  List<SkillPickStatDomain> findByMatchId(Long matchId);

  /** Matches with a stored row for the hero, the one that started last first. */
  @Query(
      """
      select d.matchId from SkillPickStatDomain d, MatchDomain m
      where m.id = d.matchId
        and d.kind = com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind.HERO
        and d.firstId = :heroId
      order by m.startTime desc, d.matchId desc
      """)
  List<Long> latestMatchesOfHero(@Param("heroId") Long heroId, Pageable page);

  List<SkillPickStatDomain> findByMatchIdAndFirstIdAndKindIn(
      Long matchId, Long firstId, Collection<SkillStatKind> kinds);

  @Transactional
  @Modifying
  @Query("delete from SkillPickStatDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
