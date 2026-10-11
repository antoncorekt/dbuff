package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** One death of a player, with the gold swing it caused. */
@Entity
@Table(
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "deathIndex"}),
    indexes = @Index(name = "idx_death_event_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeathEventDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "death_event_seq")
  @SequenceGenerator(
      name = "death_event_seq",
      sequenceName = "death_event_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  /** Account id, -1 for anonymous players. */
  private Long playerId;
  /** Order of the death within the player log. */
  private Integer deathIndex;
  /** Game time in seconds. */
  private Long deathTime;
  /** Internal name of the hero that got the kill. */
  private String killerHeroName;
  private Long goldLost;
  private Long goldFed;
  /** Seconds spent dead before respawn. */
  private Long timeDead;
}
