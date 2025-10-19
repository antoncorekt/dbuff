package com.ako.dbuff.dao.model;

import com.ako.dbuff.dao.model.id.PlayerGameStatisticDomainId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@IdClass(PlayerGameStatisticDomainId.class)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PlayerMatchStatisticDomain {

  @Id private Long playerId;

  @Id private Long matchId;

  private Long playerSlot;
  private Long obsPlaced;
  private Long senPlaced;
  private Long creepsStacked;
  private Long lastHits;
  private Long denies;
  private Long campsStacked;
  private Long runePickups;
  private Long towerKills;
  private Long roshanKills;

  private Long partySize;

  private Long heroId;
  private String heroName;
  private String heroPrettyName;

  private Long win;
  private Long totalGold;
  private Long totalXp;
  private BigDecimal kda;

  private Long abandons;

  private Long neutralKills;
  private Long courierKills;

  private Long aganim;
  private Long aganimShard;
  private Long moonshard;

  private Long lane;
  private BigDecimal laneEfficiency;
  private BigDecimal laneEfficiencyPct;

  private Long goldPerMin;
  private Long xpPerMin;

  private Boolean hasNeutralItems;
  private Boolean dotaApiItems;
  private Boolean hasItems;

  private Boolean dotaApiAbilities;
  private Boolean hasAbilities;
}
