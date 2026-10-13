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

/**
 * A stretch of whole minutes in which a team kept one posture after the lane stage: grouped, split
 * or mixed, with what happened to the team inside it.
 */
@Entity
@Table(
    name = "team_split_stretch",
    indexes = @Index(name = "idx_team_split_stretch_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamSplitStretchDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "team_split_stretch_seq")
  @SequenceGenerator(
      name = "team_split_stretch_seq",
      sequenceName = "team_split_stretch_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** RADIANT or DIRE. */
  private String team;

  /** Game seconds; the end is exclusive. */
  private Integer startSeconds;

  private Integer endSeconds;

  /** One of the Posture values. */
  private String label;

  /** Towers of the team that fell, and enemy towers it took, inside the stretch. */
  private Integer towersLost;

  private Integer towersTaken;

  /** Deaths of the team's heroes and of the enemy's heroes inside the stretch. */
  private Integer heroesLost;

  private Integer kills;
  private Integer analysisVersion;
}
