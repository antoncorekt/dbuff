package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.HeroDraftEntry;
import com.ako.dbuff.service.replay.HeroDraftFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import skadistats.clarity.model.Entity;

/** Reads the Ability Draft hero pool and its picks; one instance serves one parse. */
final class HeroDraftReader extends PoolDraftReader<HeroDraftFacts> {
  private static final String DRAFT = "m_pGameRules.m_AbilityDraftHeroes.";

  HeroDraftReader() {
    super("m_AbilityDraftHeroes");
  }

  @Override
  public HeroDraftFacts read(Entity rules) {
    List<HeroDraftEntry> pool = new ArrayList<>();
    for (int index = 0; ; index++) {
      String prefix = DRAFT + String.format(Locale.ROOT, "%02d", index) + ".";
      Number hero = number(rules, prefix + "m_nHeroID");
      Number player = number(rules, prefix + "m_unPlayerID");
      if (hero == null || hero.intValue() == 0 || player == null) {
        break;
      }
      pool.add(
          new HeroDraftEntry(
              index, hero.intValue(), player.intValue() == NOT_PICKED ? -1 : player.intValue()));
    }
    return pool.isEmpty() ? null : new HeroDraftFacts(pool, picks());
  }
}
