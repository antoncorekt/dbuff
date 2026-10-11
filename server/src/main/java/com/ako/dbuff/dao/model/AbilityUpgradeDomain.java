package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** One ability point spent by a player, in the order it was spent (talents included). */
@Entity
@Table(
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "upgradeIndex"}),
    indexes = @Index(name = "idx_ability_upgrade_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbilityUpgradeDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ability_upgrade_seq")
  @SequenceGenerator(
      name = "ability_upgrade_seq",
      sequenceName = "ability_upgrade_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  /** Account id, -1 for anonymous players. */
  private Long playerId;
  /** 1-based order of the ability point. */
  private Integer upgradeIndex;
  private Long abilityId;
}
