package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** Per-minute Radiant advantage; negative values favour Dire. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "gameMinute"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamTimelineDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "team_timeline_seq")
  @SequenceGenerator(
      name = "team_timeline_seq",
      sequenceName = "team_timeline_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer gameMinute;
  private Long radiantGoldAdv;
  private Long radiantXpAdv;
}
