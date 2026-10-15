package com.ako.dbuff.dao.repo;

import com.ako.dbuff.service.replay.analysis.skillstat.SkillCounts;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillPair;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillWindow;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Games and wins of every player in the Ability Draft games of a window, per hero, skill, hero with
 * a skill and pair of skills. Each question is one aggregate that returns counts, never rows. The
 * existing ranking queries answer for one player and collect match ids first, so they do not fit a
 * question over all players.
 */
@Repository
public class SkillCountRepository {
  private static final String WON = "sum(case when p.win = 1 then 1 else 0 end)";

  private static final String PLAYER_GAME_FILTER =
      """
join player_match_statistic_domain p on p.match_id = a.match_id and p.player_slot = a.player_slot
join match_domain m on m.id = a.match_id
where m.game_mode_id = :mode and m.start_time >= :from and m.start_time < :to
  and m.id <> :match
""";

  @PersistenceContext private EntityManager entityManager;

  public SkillCounts hero(long heroId, SkillWindow window) {
    Query query =
        bound(
            entityManager.createNativeQuery(
                "select count(*), coalesce("
                    + WON
                    + ", 0) from player_match_statistic_domain p"
                    + " join match_domain m on m.id = p.match_id"
                    + " where p.hero_id = :hero and m.game_mode_id = :mode"
                    + " and m.start_time >= :from and m.start_time < :to and m.id <> :match"),
            window);
    query.setParameter("hero", heroId);
    Object[] row = (Object[]) query.getSingleResult();
    return counts(row[0], row[1]);
  }

  public Map<Long, SkillCounts> skills(Collection<Long> skillIds, SkillWindow window) {
    return perSkill(null, skillIds, window);
  }

  public Map<Long, SkillCounts> heroWithSkills(
      long heroId, Collection<Long> skillIds, SkillWindow window) {
    return perSkill(heroId, skillIds, window);
  }

  public Map<SkillPair, SkillCounts> pairs(Collection<Long> skillIds, SkillWindow window) {
    Map<SkillPair, SkillCounts> result = new HashMap<>();
    if (skillIds.size() < 2) {
      return result;
    }
    Query query =
        bound(
            entityManager.createNativeQuery(
                "select a.ability_id, b.ability_id, count(*), coalesce("
                    + WON
                    + ", 0) from ability_domain a"
                    + " join ability_domain b on b.match_id = a.match_id"
                    + " and b.player_slot = a.player_slot and b.ability_id > a.ability_id "
                    + PLAYER_GAME_FILTER
                    + " and a.ability_id in (:skills) and b.ability_id in (:skills)"
                    + " group by a.ability_id, b.ability_id"),
            window);
    query.setParameter("skills", skillIds);
    for (Object row : query.getResultList()) {
      Object[] cols = (Object[]) row;
      result.put(
          SkillPair.of(((Number) cols[0]).longValue(), ((Number) cols[1]).longValue()),
          counts(cols[2], cols[3]));
    }
    return result;
  }

  private Map<Long, SkillCounts> perSkill(
      Long heroId, Collection<Long> skillIds, SkillWindow window) {
    Map<Long, SkillCounts> result = new HashMap<>();
    if (skillIds.isEmpty()) {
      return result;
    }
    Query query =
        bound(
            entityManager.createNativeQuery(
                "select a.ability_id, count(*), coalesce("
                    + WON
                    + ", 0) from ability_domain a "
                    + PLAYER_GAME_FILTER
                    + " and a.ability_id in (:skills)"
                    + (heroId == null ? "" : " and p.hero_id = :hero")
                    + " group by a.ability_id"),
            window);
    query.setParameter("skills", skillIds);
    if (heroId != null) {
      query.setParameter("hero", heroId);
    }
    for (Object row : (List<?>) query.getResultList()) {
      Object[] cols = (Object[]) row;
      result.put(((Number) cols[0]).longValue(), counts(cols[1], cols[2]));
    }
    return result;
  }

  private static Query bound(Query query, SkillWindow window) {
    return query
        .setParameter("mode", AbilityDraftProfile.MODE_ID)
        .setParameter("from", window.fromEpoch())
        .setParameter("to", window.toEpoch())
        .setParameter("match", window.excludedMatchId());
  }

  private static SkillCounts counts(Object games, Object wins) {
    return new SkillCounts(((Number) games).longValue(), ((Number) wins).longValue());
  }
}
