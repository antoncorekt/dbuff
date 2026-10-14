package com.ako.dbuff.service.replay;

import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Puts the skill picks and the hero picks of an Ability Draft on one line: every turn of the draft
 * is a skill or a hero, so the overall turn number counts both.
 */
@Slf4j
final class DraftTimeline {
  static final double TICKS_PER_SECOND = 30.0;

  private DraftTimeline() {}

  /**
   * The turn numbers, starting at 1, by pick order of each kind.
   *
   * @param skills turn of each skill pick, by {@link DraftPick#order()}
   * @param heroes turn of each hero pick, by {@link DraftPick#order()}
   */
  record Turns(Map<Integer, Integer> skills, Map<Integer, Integer> heroes) {
    static final Turns NONE = new Turns(Map.of(), Map.of());

    boolean known() {
      return this != NONE;
    }

    Integer skill(int order) {
      return skills.get(order);
    }

    Integer hero(int order) {
      return heroes.get(order);
    }
  }

  /**
   * None unless both sides are complete, so the tables of the two extractors agree. A skill pick
   * and a hero pick on the very same tick are ordered skill first; that is unverified, since no
   * such tie was seen in 11 real drafts.
   */
  static Turns turns(
      long matchId, AbilityDraftFacts skills, HeroDraftFacts heroes, AbilityDraftProfile profile) {
    if (!DraftShape.skillsComplete(skills, profile)
        || !DraftShape.heroesComplete(heroes, profile)) {
      return Turns.NONE;
    }
    Map<Integer, Integer> skillTurns = new HashMap<>();
    Map<Integer, Integer> heroTurns = new HashMap<>();
    List<DraftPick> skillPicks = skills.picks();
    List<DraftPick> heroPicks = heroes.picks();
    int s = 0;
    int h = 0;
    for (int turn = 1; s < skillPicks.size() || h < heroPicks.size(); turn++) {
      boolean skillNext =
          h >= heroPicks.size()
              || (s < skillPicks.size() && skillPicks.get(s).tick() <= heroPicks.get(h).tick());
      if (skillNext) {
        skillTurns.put(skillPicks.get(s++).order(), turn);
      } else {
        heroTurns.put(heroPicks.get(h++).order(), turn);
      }
    }
    int total = skillTurns.size() + heroTurns.size();
    if (total != profile.picks() + profile.heroPicks()) {
      log.warn(
          "Match {} draft has {} turns, expected {}",
          matchId,
          total,
          profile.picks() + profile.heroPicks());
    }
    return new Turns(skillTurns, heroTurns);
  }

  /** Seconds on the game clock, negative before the horn. */
  static double clockSeconds(int tick, int clockStartTick) {
    return (tick - clockStartTick) / TICKS_PER_SECOND;
  }
}
