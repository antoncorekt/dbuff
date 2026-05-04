package com.ako.dbuff.service.match.report.analyzer.item;

import lombok.Data;

@Data
public class ChosenItemPlayerReport implements ItemReport {

  private long playerId;
  private Long itemId;
  private String prettyItemName;
  private String heroPrettyName;
  private Long heroId;
  private boolean neutral;
  private Long purchaseTime;
  private int totalMatches;
  private int winCount;
  private int loseCount;
  private double winRate;
  private int historyMonths;

  @Override
  public String getName() {
    return "ChosenItemStats";
  }
}
