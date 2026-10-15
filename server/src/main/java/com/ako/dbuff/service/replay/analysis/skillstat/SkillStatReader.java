package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.SkillPickStatRepo;
import com.ako.dbuff.resources.model.SkillStatResponse;
import com.ako.dbuff.resources.model.SkillStatResponse.PlayerStats;
import com.ako.dbuff.resources.model.SkillStatResponse.Stat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Reads the stored rows for the REST API; it runs no history query. */
@Service
@RequiredArgsConstructor
public class SkillStatReader {
  private static final Comparator<SkillPickStatDomain> BEST_FIRST =
      Comparator.comparing(SkillPickStatDomain::getKind)
          .thenComparing(
              (SkillPickStatDomain d) -> SkillStatMath.wilsonLowerBound(d.getWins(), d.getGames()),
              Comparator.reverseOrder())
          .thenComparing(SkillPickStatDomain::getFirstId)
          .thenComparing(d -> d.getSecondId() == null ? 0L : d.getSecondId());

  private final SkillPickStatRepo repo;
  private final SkillStatWording wording;

  /** Empty when nothing is stored for the match. */
  public SkillStatResponse match(long matchId) {
    Map<Long, List<SkillPickStatDomain>> bySlot =
        repo.findByMatchId(matchId).stream()
            .collect(
                Collectors.groupingBy(
                    SkillPickStatDomain::getPlayerSlot, TreeMap::new, Collectors.toList()));
    List<PlayerStats> players =
        bySlot.entrySet().stream()
            .map(e -> new PlayerStats(e.getKey(), heroOf(e.getValue()), stats(e.getValue())))
            .toList();
    return new SkillStatResponse(matchId, players);
  }

  /** The newest stored figures of the hero and its skills; empty when none are stored. */
  public List<Stat> hero(long heroId) {
    return stats(repo.findLatestForHero(heroId));
  }

  private static Long heroOf(List<SkillPickStatDomain> rows) {
    return rows.stream()
        .filter(d -> d.getKind() == SkillStatKind.HERO)
        .map(SkillPickStatDomain::getFirstId)
        .findFirst()
        .orElse(null);
  }

  private List<Stat> stats(List<SkillPickStatDomain> rows) {
    return rows.stream().sorted(BEST_FIRST).map(this::stat).toList();
  }

  private Stat stat(SkillPickStatDomain d) {
    Double rate = d.getGames() == 0 ? null : (double) d.getWins() / d.getGames();
    return new Stat(
        d.getKind(),
        d.getFirstId(),
        d.getSecondId(),
        d.getMatchId(),
        d.getGames(),
        d.getWins(),
        rate,
        SkillStatMath.liftPoints(d).stream().boxed().findFirst().orElse(null),
        SkillStatMath.wilsonLowerBound(d.getWins(), d.getGames()),
        d.getWindowMonths(),
        wording.describe(d));
  }
}
