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

/** A hero whose main lane in the minutes after the laning start differs from his stored lane. */
@Entity
@Table(
    name = "team_lane_swap",
    indexes = @Index(name = "idx_team_lane_swap_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamLaneSwapDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "team_lane_swap_seq")
  @SequenceGenerator(
      name = "team_lane_swap_seq",
      sequenceName = "team_lane_swap_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** RADIANT or DIRE. */
  private String team;

  private Long playerSlot;

  /** BOTTOM, MID or TOP: the stored lane, and the lane he mostly stood in. */
  private String laneBefore;

  private String laneAfter;

  /** The first and the last second he was seen in the new lane inside the swap window. */
  private Integer fromSeconds;

  private Integer toSeconds;
  private Integer analysisVersion;
}
