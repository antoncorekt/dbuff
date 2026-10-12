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
 * One possible reason for how a fight went, in a shape shared by every mode. Every reason found is
 * kept; which of them to tell is decided by the reader. A team reason has no subject slot, so the
 * unique key does not hold it to one row.
 */
@Entity
@Table(
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"matchId", "fightIndex", "subjectSlot", "code"}),
    indexes = @Index(name = "idx_fight_reason_match_fight", columnList = "matchId, fightIndex"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FightReasonDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fight_reason_seq")
  @SequenceGenerator(
      name = "fight_reason_seq",
      sequenceName = "fight_reason_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer fightIndex;

  /** Null for a reason about the team. */
  private Long subjectSlot;

  /** RADIANT or DIRE: the subject's team. */
  private String team;

  /** PLAYER or TEAM. */
  private String scope;

  private String code;

  /** 0 to 1, a hypothesis of how much the reason explains the fight. */
  private Double severity;

  @Column(length = 1000)
  private String evidence;

  /** COMMON, or the mode that added the reason. */
  private String source;

  private Integer analysisVersion;
}
