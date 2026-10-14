package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.DraftPick;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntSupplier;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;

/**
 * The common part of the Ability Draft readers: a pool is an array of entries on the game rules,
 * each with a player id that changes from "nobody" to a player at the moment of the pick.
 */
abstract class PoolDraftReader<T> implements DraftReader<T> {
  static final int NOT_PICKED = 20;
  private static final String RULES = "m_pGameRules.";
  private static final String PICK_SUFFIX = ".m_unPlayerID";

  private final String prefix;
  private final int expectedPicks;
  private final List<DraftPick> picks = new ArrayList<>();
  private final BitSet picked = new BitSet();

  /**
   * @param array the name of the pool array on the game rules, e.g. {@code m_AbilityDraftHeroes}
   * @param expectedPicks the picks of a complete draft
   */
  PoolDraftReader(String array, int expectedPicks) {
    this.prefix = RULES + array + ".";
    this.expectedPicks = expectedPicks;
  }

  @Override
  public boolean done() {
    return picks.size() >= expectedPicks;
  }

  /** A pick is the moment an entry's player id changes from "nobody" to a player. */
  @Override
  public void observe(Entity rules, FieldPath path, String name, IntSupplier gameTick) {
    if (!name.startsWith(prefix) || !name.endsWith(PICK_SUFFIX)) {
      return;
    }
    int poolIndex;
    try {
      poolIndex = Integer.parseInt(name, prefix.length(), name.length() - PICK_SUFFIX.length(), 10);
    } catch (NumberFormatException e) {
      return;
    }
    int player = ((Number) rules.getPropertyForFieldPath(path)).intValue();
    if (player != NOT_PICKED && !picked.get(poolIndex)) {
      picked.set(poolIndex);
      picks.add(new DraftPick(picks.size() + 1, poolIndex, player, gameTick.getAsInt()));
    }
  }

  List<DraftPick> picks() {
    return List.copyOf(picks);
  }

  /** The array has room past its last entry, where the property exists but holds nothing. */
  static Number number(Entity rules, String property) {
    return rules.hasProperty(property) ? (Number) rules.getProperty(property) : null;
  }
}
