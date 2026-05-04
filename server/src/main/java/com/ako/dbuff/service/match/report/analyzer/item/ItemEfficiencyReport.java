package com.ako.dbuff.service.match.report.analyzer.item;

import com.ako.dbuff.service.match.report.analyzer.PerPlayerReport;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class ItemEfficiencyReport implements PerPlayerReport {

  private long playerId;
  private String groupName;
  private List<Long> itemIds;
  private List<String> itemNames;
  private int totalMatches;
  private int winCount;
  private int loseCount;
  private double winRate;
  private Map<Long, Double> avgPurchaseTimeByItem;
  private Map<Long, Long> currentPurchaseTimeByItem;
  private Map<String, Object> props;
  private int historyMonths;

  @Override
  public String getName() {
    return "ItemEfficiency";
  }
}
