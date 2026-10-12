package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.AbilityDraftFacts;
import com.ako.dbuff.service.replay.DraftPick;
import com.ako.dbuff.service.replay.DraftPoolEntry;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;

/** Reads the Ability Draft pool and its picks; one instance serves one parse. */
final class AbilityDraftReader implements DraftReader {
  private static final int NOT_PICKED = 20;
  private static final String DRAFT = "m_pGameRules.m_AbilityDraftAbilities.";
  private static final Pattern PICK_FIELD =
      Pattern.compile("^m_pGameRules\\.m_AbilityDraftAbilities\\.(\\d+)\\.m_unPlayerID$");

  private final List<DraftPick> picks = new ArrayList<>();
  private final BitSet picked = new BitSet();

  /** A pick is the moment an entry's player id changes from "nobody" to a player. */
  @Override
  public void observe(Entity rules, FieldPath[] paths, int count, IntSupplier gameTick) {
    for (int i = 0; i < count; i++) {
      Matcher match = PICK_FIELD.matcher(rules.getNameForFieldPath(paths[i]));
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

  /** The final pool, read when the game starts so that moved skills show their final slot. */
  @Override
  public AbilityDraftFacts read(Entity rules) {
    List<DraftPoolEntry> pool = new ArrayList<>();
    for (int index = 0; ; index++) {
      String prefix = DRAFT + String.format(Locale.ROOT, "%04d", index) + ".";
      Number ability = number(rules, prefix + "m_nAbilityID");
      Number player = number(rules, prefix + "m_unPlayerID");
      Number slot = number(rules, prefix + "m_unAbilityPlayerSlot");
      if (ability == null || ability.intValue() == 0 || player == null || slot == null) {
        break;
      }
      pool.add(
          new DraftPoolEntry(
              index,
              ability.intValue() / 2,
              player.intValue() == NOT_PICKED ? -1 : player.intValue(),
              slot.intValue()));
    }
    return pool.isEmpty() ? null : new AbilityDraftFacts(pool, List.copyOf(picks));
  }

  /** The array has room past its last entry, where the property exists but holds nothing. */
  private static Number number(Entity rules, String property) {
    return rules.hasProperty(property) ? (Number) rules.getProperty(property) : null;
  }
}
