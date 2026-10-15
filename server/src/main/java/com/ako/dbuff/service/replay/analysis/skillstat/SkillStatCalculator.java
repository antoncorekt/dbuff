package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.SkillCountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The rows of one player: the hero, each skill held, the hero with each skill and every pair of
 * skills held. A pair also keeps what each of its skills did without the other, which is the
 * skill's own count minus the pair's, so no further query is needed.
 */
@Component
@RequiredArgsConstructor
public class SkillStatCalculator {
  private final SkillCountRepository counts;
  private final SkillStatProperties properties;

  public List<SkillPickStatDomain> forPlayer(
      long matchId,
      long playerSlot,
      Long heroId,
      Iterable<Long> heldSkills,
      SkillWindow window,
      int analysisVersion) {
    TreeSet<Long> skills = new TreeSet<>();
    heldSkills.forEach(skills::add);
    SkillPickStatDomain template =
        SkillPickStatDomain.builder()
            .matchId(matchId)
            .playerSlot(playerSlot)
            .windowMonths(properties.getWindowMonths())
            .analysisVersion(analysisVersion)
            .build();
    List<SkillPickStatDomain> rows = new ArrayList<>();
    if (heroId != null) {
      rows.add(row(template, SkillStatKind.HERO, heroId, null, counts.hero(heroId, window)));
    }
    Map<Long, SkillCounts> single = counts.skills(skills, window);
    for (long skill : skills) {
      rows.add(
          row(
              template,
              SkillStatKind.SKILL,
              skill,
              null,
              single.getOrDefault(skill, SkillCounts.NONE)));
    }
    if (heroId != null) {
      Map<Long, SkillCounts> withHero = counts.heroWithSkills(heroId, skills, window);
      for (long skill : skills) {
        rows.add(
            row(
                template,
                SkillStatKind.HERO_SKILL,
                heroId,
                skill,
                withHero.getOrDefault(skill, SkillCounts.NONE)));
      }
    }
    Map<SkillPair, SkillCounts> pairs = counts.pairs(skills, window);
    for (long first : skills) {
      for (long second : skills.tailSet(first, false)) {
        SkillCounts together = pairs.getOrDefault(new SkillPair(first, second), SkillCounts.NONE);
        SkillPickStatDomain pair =
            pairRow(
                template,
                first,
                second,
                together,
                single.getOrDefault(first, SkillCounts.NONE),
                single.getOrDefault(second, SkillCounts.NONE));
        rows.add(pair);
      }
    }
    return rows;
  }

  static SkillPickStatDomain pairRow(
      SkillPickStatDomain template,
      long first,
      long second,
      SkillCounts together,
      SkillCounts firstAll,
      SkillCounts secondAll) {
    SkillCounts firstWithout = firstAll.minus(together);
    SkillCounts secondWithout = secondAll.minus(together);
    SkillPickStatDomain pair = row(template, SkillStatKind.SKILL_PAIR, first, second, together);
    pair.setFirstGamesWithout(firstWithout.games());
    pair.setFirstWinsWithout(firstWithout.wins());
    pair.setSecondGamesWithout(secondWithout.games());
    pair.setSecondWinsWithout(secondWithout.wins());
    return pair;
  }

  static SkillPickStatDomain row(
      SkillPickStatDomain template,
      SkillStatKind kind,
      long firstId,
      Long secondId,
      SkillCounts counts) {
    return SkillPickStatDomain.builder()
        .matchId(template.getMatchId())
        .playerSlot(template.getPlayerSlot())
        .windowMonths(template.getWindowMonths())
        .analysisVersion(template.getAnalysisVersion())
        .kind(kind)
        .firstId(firstId)
        .secondId(secondId)
        .games(counts.games())
        .wins(counts.wins())
        .build();
  }
}
