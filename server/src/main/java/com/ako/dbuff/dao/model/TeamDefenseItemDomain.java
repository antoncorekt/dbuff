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

/** A hero getting a defensive item of the catalog, or buying it again after losing it. */
@Entity
@Table(indexes = @Index(name = "idx_team_defense_item_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDefenseItemDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "team_defense_item_seq")
  @SequenceGenerator(
      name = "team_defense_item_seq",
      sequenceName = "team_defense_item_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** RADIANT or DIRE. */
  private String team;

  private Long playerSlot;

  /** The item key without the {@code item_} prefix. */
  private String item;

  /** MAGIC, PHYSICAL, HP or STATUS. */
  private String kind;

  private Integer atSecond;
  private Integer analysisVersion;
}
