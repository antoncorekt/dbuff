package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.SkillCountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Answers the {@code /skills} questions from the same counts as the stored statistics: the last
 * months of Ability Draft before now, no minimum number of games, nothing stored or changed. Rows
 * with the most reliable win rate (Wilson bound) come first; the rate shown stays the raw one.
 */
@Component
public class SkillLookup {
  private static final long NO_EXCLUDED_MATCH = -1;
  private static final Comparator<SkillPickStatDomain> RELIABILITY =
      Comparator.comparingDouble(
          row -> SkillStatMath.wilsonLowerBound(row.getWins(), row.getGames()));

  private final SkillCountRepository counts;
  private final SkillStatProperties properties;
  private final Clock clock;

  @Autowired
  public SkillLookup(SkillCountRepository counts, SkillStatProperties properties) {
    this(counts, properties, Clock.systemUTC());
  }

  SkillLookup(SkillCountRepository counts, SkillStatProperties properties, Clock clock) {
    this.counts = counts;
    this.properties = properties;
    this.clock = clock;
  }

  /** The hero and those of its skills that have games, most reliable first; none if no game. */
  public SkillAnswer hero(long heroId, List<Long> skillIds) {
    SkillWindow window = window();
    SkillPickStatDomain template = template();
    SkillPickStatDomain subject =
        SkillStatCalculator.row(
            template, SkillStatKind.HERO, heroId, null, counts.hero(heroId, window));
    if (subject.getGames() == 0) {
      return new SkillAnswer(subject, List.of());
    }
    Map<Long, SkillCounts> withHero = counts.heroWithSkills(heroId, skillIds, window);
    List<SkillPickStatDomain> details =
        skillIds.stream()
            .map(
                skill ->
                    SkillStatCalculator.row(
                        template,
                        SkillStatKind.HERO_SKILL,
                        heroId,
                        skill,
                        withHero.getOrDefault(skill, SkillCounts.NONE)))
            .filter(row -> row.getGames() > 0)
            .sorted(RELIABILITY.reversed())
            .toList();
    return new SkillAnswer(subject, details);
  }

  /** The skill alone and the hero it won most reliably on; no detail when no game had it. */
  public SkillAnswer skill(long skillId) {
    SkillWindow window = window();
    SkillPickStatDomain template = template();
    SkillCounts own =
        counts.skills(Set.of(skillId), window).getOrDefault(skillId, SkillCounts.NONE);
    SkillPickStatDomain subject =
        SkillStatCalculator.row(template, SkillStatKind.SKILL, skillId, null, own);
    Optional<SkillPickStatDomain> best =
        counts.heroesWithSkill(skillId, window).entrySet().stream()
            .map(
                entry ->
                    SkillStatCalculator.row(
                        template,
                        SkillStatKind.HERO_SKILL,
                        entry.getKey(),
                        skillId,
                        entry.getValue()))
            .max(RELIABILITY);
    return new SkillAnswer(subject, best.stream().toList());
  }

  public SkillAnswer pair(long first, long second) {
    SkillWindow window = window();
    SkillPair pair = SkillPair.of(first, second);
    Map<Long, SkillCounts> single = counts.skills(Set.of(first, second), window);
    SkillCounts together =
        counts.pairs(Set.of(first, second), window).getOrDefault(pair, SkillCounts.NONE);
    return new SkillAnswer(
        SkillStatCalculator.pairRow(
            template(),
            pair.first(),
            pair.second(),
            together,
            single.getOrDefault(pair.first(), SkillCounts.NONE),
            single.getOrDefault(pair.second(), SkillCounts.NONE)),
        List.of());
  }

  private SkillWindow window() {
    Instant now = clock.instant();
    long from =
        now.atZone(ZoneOffset.UTC).minusMonths(properties.getWindowMonths()).toEpochSecond();
    return new SkillWindow(from, now.getEpochSecond() + 1, NO_EXCLUDED_MATCH);
  }

  private SkillPickStatDomain template() {
    return SkillPickStatDomain.builder().windowMonths(properties.getWindowMonths()).build();
  }
}
