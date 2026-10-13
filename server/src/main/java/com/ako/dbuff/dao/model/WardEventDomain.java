package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** One ward a player placed, joined with its removal when the log has one. */
@Entity
@Table(
    name = "ward_event",
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "wardIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WardEventDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ward_event_seq")
  @SequenceGenerator(name = "ward_event_seq", sequenceName = "ward_event_seq", allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  private Long playerId;

  /** Order of the ward among the player's observers and sentries, by placement time. */
  private Integer wardIndex;

  @Enumerated(EnumType.STRING)
  private WardKind wardKind;

  /** Game seconds; negative before the horn. */
  private Long placedTime;

  private Double x;
  private Double y;

  /** The game entity handle; it ties the removal to the placement. */
  private Long ehandle;

  private Long removedTime;

  /** Internal name of the hero the log credits with the removal; the owner for an expiry. */
  private String removedBy;

  @Enumerated(EnumType.STRING)
  private WardRemovalKind removalKind;
}
