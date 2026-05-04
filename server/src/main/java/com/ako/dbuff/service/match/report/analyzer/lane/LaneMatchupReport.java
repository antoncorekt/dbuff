package com.ako.dbuff.service.match.report.analyzer.lane;

import com.ako.dbuff.service.match.report.analyzer.Report;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class LaneMatchupReport implements Report {

  private int laneId;
  private String laneName;
  private List<LanePlayerSummary> radiantPlayers;
  private List<LanePlayerSummary> direPlayers;
  private String laneOutcome;
  private long radiantGoldAt10;
  private long direGoldAt10;
  private long radiantXpAt10;
  private long direXpAt10;

  @Override
  public String getName() {
    return "LaneMatchup";
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class LanePlayerSummary {
    private long playerId;
    private long playerSlot;
    private String heroPrettyName;
    private boolean focusPlayer;
    private Long laneEfficiencyPct;
    private long denies;
    private long lastHitsAt5;
    private long lastHitsAt10;
    private long lastHitsAt15;
    private long lastHitsAt20;
    private long goldAt5;
    private long goldAt10;
    private long goldAt15;
    private long goldAt20;
    private long xpAt5;
    private long xpAt10;
    private long xpAt15;
    private long xpAt20;
    private int laneKills;
    private int laneDeaths;
    private List<ItemEvent> earlyItems;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ItemEvent {
    private String prettyName;
    private long purchaseTime;
  }
}
