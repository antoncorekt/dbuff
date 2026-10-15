package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SkillPickStatRepo extends JpaRepository<SkillPickStatDomain, Long> {

  List<SkillPickStatDomain> findByMatchId(Long matchId);

  /**
   * The newest stored figures of the hero and of each of its skills, whatever match they came from.
   */
  @Query(
      """
select d from SkillPickStatDomain d
where d.firstId = :heroId and d.kind in (com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind.HERO,
      com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind.HERO_SKILL)
  and d.matchId = (select max(d2.matchId) from SkillPickStatDomain d2
      where d2.kind = d.kind and d2.firstId = d.firstId
        and coalesce(d2.secondId, -1) = coalesce(d.secondId, -1))
""")
  List<SkillPickStatDomain> findLatestForHero(@Param("heroId") Long heroId);

  @Transactional
  @Modifying
  @Query("delete from SkillPickStatDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
