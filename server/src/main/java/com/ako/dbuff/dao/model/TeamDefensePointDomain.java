package com.ako.dbuff.dao.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A team's summed health and effective health at one game minute, with the damage shares the enemy
 * dealt it. The last sample has an extra row with checkpoint END.
 */
@Entity
@Table(indexes = @Index(name = "idx_team_defense_point_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDefensePointDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "team_defense_point_seq")
  @SequenceGenerator(
      name = "team_defense_point_seq",
      sequenceName = "team_defense_point_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** RADIANT or DIRE. */
  private String team;

  private Integer minute;
  private Integer maxHpSum;
  private Integer hpSum;
  private Integer armorAvgX10;
  private Integer magicResistAvgX10;
  private Integer ehpPhys;
  private Integer ehpMag;
  private Integer ehpMix;
  private Double enemyPhysShare;
  private Double enemyMagShare;
  private Double enemyPureShare;
  private Integer alive;
  private Integer netWorth;

  /** M10, M20, M30, END or null. */
  private String checkpoint;

  private Integer analysisVersion;
}
