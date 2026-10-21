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
 * Whether the skills a player drafted fit the role he was meant to play and the role he played. One
 * row when both roles are the same (stage BOTH), else one per role (INTENT and FINAL).
 */
@Entity
@Table(
    name = "build_fit",
    indexes = @Index(name = "idx_build_fit_match", columnList = "matchId"),
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "stage"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildFitDomain {
  public static final int FUNCTIONS_LENGTH = 200;
  public static final int EVIDENCE_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "build_fit_seq")
  @SequenceGenerator(name = "build_fit_seq", sequenceName = "build_fit_seq", allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** INTENT, FINAL, or BOTH when the drafted and the played role are the same. */
  private String stage;

  /** 1 carry, 2 mid, 3 offlane, 4 roaming support, 5 hard support. */
  private Integer role;

  /** The functions of the role's needs that the build lacks, comma separated; empty for none. */
  @Column(length = FUNCTIONS_LENGTH)
  private String missing;

  /** The missing functions the match backs (a subset of {@code missing}), comma separated. */
  @Column(length = FUNCTIONS_LENGTH)
  private String stated;

  /** FIT, GAPS_NOT_SHOWN or GAP (see {@code BuildFitVerdict}). */
  @Column(length = 20)
  private String verdict;

  /** The wanted functions the build has no value for, comma separated; empty for none. */
  @Column(name = "unknown_functions", length = FUNCTIONS_LENGTH)
  private String unknown;

  /** The stated gaps in plain words; a short fit sentence when none is stated. */
  @Column(length = EVIDENCE_LENGTH)
  private String evidence;

  private Integer analysisVersion;
}
