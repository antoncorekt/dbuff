package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.service.replay.analysis.defense.DefenseItemBonuses.Bonus;
import java.util.Collection;

public final class DefenseMath {
  static final double MAX_MAGIC_RESIST = 0.95;
  private static final double ARMOR_FACTOR = 0.06;

  private DefenseMath() {}

  /** Item agility is not added: it is already inside the agility total. */
  public static double armor(
      double baseArmor, double agilityTotal, double armorPerAgility, Collection<Bonus> items) {
    double armor = baseArmor + agilityTotal * armorPerAgility;
    for (Bonus item : items) {
      armor += item.armor();
    }
    return armor;
  }

  public static double magicResist(double baseMr, Collection<Bonus> items) {
    double kept = 1 - baseMr;
    for (Bonus item : items) {
      kept *= 1 - item.magicResist();
    }
    return Math.min(1 - kept, MAX_MAGIC_RESIST);
  }

  public static double physicalMultiplier(double armor) {
    return 1 - ARMOR_FACTOR * armor / (1 + ARMOR_FACTOR * Math.abs(armor));
  }

  public static double ehpPhysical(double maxHp, double armor) {
    return maxHp / physicalMultiplier(armor);
  }

  public static double ehpMagical(double maxHp, double mr) {
    return maxHp / (1 - Math.min(mr, MAX_MAGIC_RESIST));
  }

  /** p, q and r are the physical, magical and pure shares of the damage taken. */
  public static double ehpMixed(
      double maxHp, double armor, double mr, double p, double q, double r) {
    double taken = p * physicalMultiplier(armor) + q * (1 - Math.min(mr, MAX_MAGIC_RESIST)) + r;
    return maxHp / taken;
  }
}
