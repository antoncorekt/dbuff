package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.DraftPick;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;

/**
 * The common part of the Ability Draft readers: a pool is an array of entries on the game rules,
 * each with a player id that changes from "nobody" to a player at the moment of the pick.
 */
abstract class PoolDraftReader<T> implements DraftReader<T> {
  static final int NOT_PICKED = 20;
  private static final String RULES = "m_pGameRules.";

  private final Pattern pickField;
  private final List<DraftPick> picks = new ArrayList<>();
  private final BitSet picked = new BitSet();

  /**
   * @param array the name of the pool array on the game rules, e.g. {@code m_AbilityDraftHeroes}
   */
  PoolDraftReader(String array) {
    this.pickField =
        Pattern.compile("^" + Pattern.quote(RULES + array + ".") + "(\\d+)\\.m_unPlayerID$");
  }

  @Override
  public void observe(Entity rules, FieldPath[] paths, int count, IntSupplier gameTick) {
    for (int i = 0; i < count; i++) {
      Matcher match = pickField.matcher(rules.getNameForFieldPath(paths[i]));
      if (!match.matches()) {
        continue;
      }
      int poolIndex = Integer.parseInt(match.group(1));
      int player = ((Number) rules.getPropertyForFieldPath(paths[i])).intValue();
      if (player != NOT_PICKED && !picked.get(poolIndex)) {
        picked.set(poolIndex);
        picks.add(new DraftPick(picks.size() + 1, poolIndex, player, gameTick.getAsInt()));
      }
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
