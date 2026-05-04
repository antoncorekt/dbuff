package com.ako.dbuff.service.match.report.analyzer.ability;

import lombok.Data;

@Data
public class AbilityHistoryStatReport implements AbilityReport {

  private long playerId;
  private Long abilityId;
  private String prettyAbilityName;
  private String heroPrettyName;
  private Long heroId;
  private long currentDamageDealt;
  private long currentDamageReceived;
  private long currentUseCount;
  private double avgDamageDealt;
  private double avgDamageReceived;
  private double avgUseCount;
  private long historicalMatchCount;

  @Override
  public String getName() {
    return "AbilityHistoryStats";
  }
}
