package com.ako.dbuff.service.replay.analysis.vision;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** What the rules say about a team's vision at a fight, with the words a report uses. */
@Getter
@RequiredArgsConstructor
public enum VisionVerdict {
  NO_VISION("no vision near the fight"),
  INVISIBLE_HERO_NO_DETECTION("invisible hero without detection"),
  LATE_WARD("ward placed just before the fight"),
  FINE("fine");

  private final String words;

  /**
   * Every verdict that applies, in this order, or {@link #FINE} alone. No observer covering the
   * fight is {@link #NO_VISION}; an enemy hero who was invisible while the team had no detection is
   * {@link #INVISIBLE_HERO_NO_DETECTION}; no full-weight observer but at least one late one
   * covering the fight is {@link #LATE_WARD}. How many observers there are does not matter.
   */
  public static List<VisionVerdict> of(TeamWards wards, boolean enemyInvisible, boolean detection) {
    List<VisionVerdict> verdicts = new ArrayList<>();
    if (wards.observers() == 0) {
      verdicts.add(NO_VISION);
    }
    if (enemyInvisible && !detection) {
      verdicts.add(INVISIBLE_HERO_NO_DETECTION);
    }
    if (wards.observers() > 0 && wards.fullObservers() == 0) {
      verdicts.add(LATE_WARD);
    }
    return verdicts.isEmpty() ? List.of(FINE) : verdicts;
  }
}
