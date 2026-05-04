package com.ako.dbuff.service.match.report.analyzer.damage;

import lombok.Data;

@Data
public class DamageHistoryReport implements DamageReport {

  private long playerId;
  private String heroPrettyName;
  private Long heroId;

  private long currentHeroDamage;
  private long currentTowerDamage;
  private long currentHeroHealing;
  private long currentDamageTaken;

  private double avgHeroDamage;
  private double avgTowerDamage;
  private double avgHeroHealing;
  private double avgDamageTaken;

  private long historicalMatchCount;
  private int historyMonths;

  @Override
  public String getName() {
    return "DamageHistory";
  }
}
