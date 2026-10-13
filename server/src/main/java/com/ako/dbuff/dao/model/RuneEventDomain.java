package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** One rune a player picked up. */
@Entity
@Table(
    name = "rune_event",
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "runeIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuneEventDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rune_event_seq")
  @SequenceGenerator(name = "rune_event_seq", sequenceName = "rune_event_seq", allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  private Long playerId;

  /** Order of the pickup within the player log. */
  private Integer runeIndex;

  /** Game seconds; negative before the horn. */
  private Long eventTime;

  /** OpenDota's rune code, kept as sent. */
  private Integer runeType;
}
