package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.dao.model.FightDomain;
import java.util.List;

/** Finds the fight a moment belongs to. */
public final class FightLookup {
  private static final double SLACK_SECONDS = 1;

  private FightLookup() {}

  /** The first fight whose span, with a second of slack on each side, holds the time, or null. */
  public static FightDomain at(List<FightDomain> fights, double second) {
    return fights.stream()
        .filter(
            f ->
                second >= f.getStartSeconds() - SLACK_SECONDS
                    && second <= f.getEndSeconds() + SLACK_SECONDS)
        .findFirst()
        .orElse(null);
  }
}
