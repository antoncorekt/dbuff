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

/** A player whose share of his team's net worth does not match how much he took part. */
@Entity
@Table(indexes = @Index(name = "idx_player_outlier_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerOutlierDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "player_outlier_seq")
  @SequenceGenerator(
      name = "player_outlier_seq",
      sequenceName = "player_outlier_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** NW_OUTLIER (a big share with little taking part) or FEEDER (a small share and many deaths). */
  private String kind;

  private Integer startSeconds;
  private Integer endSeconds;

  /** The most extreme share of the team's net worth in the stretch, in percent. */
  private Double sharePercent;

  /** His place in his team by fights joined, 1 being the most. */
  private Integer participationRank;

  private Integer fightsJoined;
  private Integer deaths;
  private Integer analysisVersion;
}
