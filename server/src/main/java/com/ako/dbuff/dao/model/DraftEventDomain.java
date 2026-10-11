package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** Hero pick or ban in an All Pick or Captains Mode draft; absent for Ability Draft. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "draftOrder"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftEventDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "draft_event_seq")
  @SequenceGenerator(
      name = "draft_event_seq",
      sequenceName = "draft_event_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** Position in the draft sequence, from OpenDota picks_bans.order. */
  private Integer draftOrder;

  /** True for a pick, false for a ban. */
  private Boolean pick;

  private Long heroId;

  /** 0 = Radiant, 1 = Dire. */
  private Integer team;
}
