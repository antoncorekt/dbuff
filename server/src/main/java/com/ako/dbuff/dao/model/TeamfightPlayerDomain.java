package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** One player's contribution to one teamfight. */
@Entity
@Table(
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "fightIndex", "playerSlot"}),
    indexes = @Index(name = "idx_teamfight_player_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamfightPlayerDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "teamfight_player_seq")
  @SequenceGenerator(
      name = "teamfight_player_seq",
      sequenceName = "teamfight_player_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer fightIndex;
  private Long playerSlot;
  private Long deaths;
  private Long buybacks;
  private Long damage;
  private Long healing;
  private Long goldDelta;
  private Long xpDelta;
}
