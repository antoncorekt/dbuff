package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One possible reason for how a side did in a lane. Every reason found is kept, ranked by severity;
 * which of them to tell is decided by the reader.
 */
@Entity
@Table(
    name = "lane_reason",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"matchId", "pairId", "team", "code", "subjectSlot"}),
    indexes = @Index(name = "idx_lane_reason_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaneReasonDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lane_reason_seq")
  @SequenceGenerator(
      name = "lane_reason_seq",
      sequenceName = "lane_reason_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer pairId;

  /** RADIANT or DIRE: the side the reason is about. */
  private String team;

  /** 1 is the strongest reason of the side in this lane. */
  private Integer rank;

  private String code;

  /** HELPED or HURT: what the reason did to the side. */
  private String direction;

  /** The hero the reason is about; null for a reason about the side. */
  private Long subjectSlot;

  /** 0 to 1, a hypothesis of how much the reason explains the result. */
  private Double severity;

  @Column(length = 1000)
  private String evidence;

  /** COMMON, or the mode that added the reason. */
  private String source;

  private Integer analysisVersion;
}
