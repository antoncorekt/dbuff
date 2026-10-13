package com.ako.dbuff.service.replay.analysis.lane;

/**
 * The result rule: each term compares the side with the other side and lies between -1 (the other
 * side far ahead) and 1; the weighted sum is the margin. A tower of the lane that fell moves it by
 * the tower bonus. A margin inside the even band is EVEN. Each term compares the averages per hero
 * of the side, so two heroes against one are not favoured by their number; the report keeps the
 * totals.
 */
final class LaneVerdict {
  private final LaneProperties properties;

  LaneVerdict(LaneProperties properties) {
    this.properties = properties;
  }

  record Verdict(LaneResult result, double margin) {}

  Verdict decide(SideTotals own, SideTotals enemy) {
    if (enemy.heroes() == 0) {
      return new Verdict(LaneResult.UNCONTESTED, 1);
    }
    double ownHeroes = Math.max(1, own.heroes());
    double enemyHeroes = Math.max(1, enemy.heroes());
    double margin =
        properties.getCreepWeight()
                * share(own.creepScore() / ownHeroes, enemy.creepScore() / enemyHeroes)
            + properties.getXpGainWeight()
                * share(orZero(own.xp()) / ownHeroes, orZero(enemy.xp()) / enemyHeroes)
            + properties.getLevelWeight()
                * clamp((own.averageLevel() - enemy.averageLevel()) / properties.getLevelFullLead())
            + properties.getKillWeight()
                * clamp(
                    (own.kills() / ownHeroes - enemy.kills() / enemyHeroes)
                        / properties.getKillFullLead())
            + properties.getDenyWeight()
                * clamp(
                    (own.denies() / ownHeroes - enemy.denies() / enemyHeroes)
                        / properties.getDenyFullLead())
            + (own.enemyTowerFell() ? properties.getTowerBonus() : 0)
            - (own.ownTowerFell() ? properties.getTowerBonus() : 0);
    LaneResult result =
        Math.abs(margin) < properties.getEvenBand()
            ? LaneResult.EVEN
            : margin > 0 ? LaneResult.WON : LaneResult.LOST;
    return new Verdict(result, margin);
  }

  private static double share(double own, double enemy) {
    double total = own + enemy;
    return total <= 0 ? 0 : (own - enemy) / total;
  }

  private static double clamp(double value) {
    return Math.max(-1, Math.min(1, value));
  }

  private static double orZero(Double value) {
    return value == null ? 0 : value;
  }
}
