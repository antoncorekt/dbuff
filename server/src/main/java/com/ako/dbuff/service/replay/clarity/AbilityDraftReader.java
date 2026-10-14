package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.AbilityDraftFacts;
import com.ako.dbuff.service.replay.DraftPoolEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import skadistats.clarity.model.Entity;

/** Reads the Ability Draft skill pool and its picks; one instance serves one parse. */
final class AbilityDraftReader extends PoolDraftReader<AbilityDraftFacts> {
  private static final String DRAFT = "m_pGameRules.m_AbilityDraftAbilities.";

  AbilityDraftReader(int expectedPicks) {
    super("m_AbilityDraftAbilities", expectedPicks);
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
    return pool.isEmpty() ? null : new AbilityDraftFacts(pool, picks());
  }
}
