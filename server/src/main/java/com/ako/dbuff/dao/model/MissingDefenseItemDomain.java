package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
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
 * A team that took mostly one damage type up to a checkpoint and had no defensive item of that
 * kind, with how stored games with and without such an item went.
 */
@Entity
@Table(indexes = @Index(name = "idx_missing_defense_item_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissingDefenseItemDomain {
  public static final int EVIDENCE_LENGTH = 400;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "missing_defense_item_seq")
  @SequenceGenerator(
      name = "missing_defense_item_seq",
      sequenceName = "missing_defense_item_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** RADIANT or DIRE. */
  private String team;

  /** M20 or M30. */
  private String checkpoint;

  /** MAGIC or PHYSICAL. */
  private String kind;

  private Integer minute;

  /** The share of the damage the team took, of the missing kind, 0 to 1. */
  private Double enemyShare;

  private Long withGames;
  private Long withWins;
  private Long withoutGames;
  private Long withoutWins;

  @Column(length = EVIDENCE_LENGTH)
  private String evidence;

  private Integer analysisVersion;
}
