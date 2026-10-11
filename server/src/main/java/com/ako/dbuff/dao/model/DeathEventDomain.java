package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** One death of a player, with the gold swing it caused. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "deathIndex"}))
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
  private Long playerId;
  /** Order of the death within the player log. */
  private Integer deathIndex;
  private Long deathTime;
  /** Internal name of the hero that got the kill. */
  private String killerHeroName;
  private Long goldLost;
  private Long goldFed;
  /** Seconds spent dead before respawn. */
  private Long timeDead;
}
