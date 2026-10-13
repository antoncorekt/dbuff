package com.ako.dbuff.service.replay.analysis.vision;

import java.util.Map;
import java.util.Set;

/** What makes a hero invisible: abilities (by their hero) and items. */
public interface InvisibleHeroes {
  /**
   * @param passive the hero is invisible while he owns the ability, with nothing to cast
   */
  record Source(String hero, String ability, boolean passive) {}

  /** The invisibility abilities by ability name. */
  Map<String, Source> abilities();

  /** Internal names of the items that make their user invisible. */
  Set<String> items();
}
