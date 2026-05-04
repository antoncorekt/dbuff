package com.ako.dbuff.service.match.report.analyzer.item;

import lombok.Data;

@Data
public class ItemHistoryStatReport implements ItemReport {

  private long playerId;
  private Long itemId;
  private String prettyItemName;
  private String heroPrettyName;
  private Long heroId;
  private long currentDamageDealt;
  private long currentDamageReceived;
  private long currentUseCount;
  private double avgDamageDealt;
  private double avgDamageReceived;
  private double avgUseCount;
  private long historicalMatchCount;
  private int historyMonths;

  @Override
  public String getName() {
    return "ItemHistoryStats";
  }
}
