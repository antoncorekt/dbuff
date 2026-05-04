package com.ako.dbuff.service.match.report.analyzer.ability;

import lombok.Data;

@Data
public class ChosenAbilityPlayerReport implements AbilityReport {

  private long playerId;
  private Long abilityId;
  private String prettyAbilityName;
  private String heroPrettyName;
  private Long heroId;
  private int totalMatches;
  private int winCount;
  private int loseCount;
  private double winRate;
  private int historyMonths;

  @Override
  public String getName() {
    return "ChosenAbilityStats";
  }
}
