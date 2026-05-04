package com.ako.dbuff.service.match.report.analyzer;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class OtherPlayerReport implements PerPlayerReport {

  private long playerId;
  private long playerSlot;
  private String heroPrettyName;
  private boolean radiant;
  private List<AbilitySummary> abilities;
  private List<ItemSummary> items;

  private long heroDamage;
  private long towerDamage;
  private long heroHealing;
  private long damageTaken;

  @Override
  public String getName() {
    return "OtherPlayer";
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class AbilitySummary {
    private String prettyName;
    private Long damageDealt;
    private Long useCount;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ItemSummary {
    private String prettyName;
    private Long purchaseTime;
    private boolean neutral;
  }
}
