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

/** What a team's player profiles say about its composition at one stage. */
@Entity
@Table(
    name = "team_composition",
    indexes = @Index(name = "idx_team_composition_match", columnList = "matchId"),
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "team", "stage"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamCompositionDomain {
  public static final int TOKENS_LENGTH = 300;
  public static final int SUMMARY_LENGTH = 600;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "team_composition_seq")
  @SequenceGenerator(
      name = "team_composition_seq",
      sequenceName = "team_composition_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** RADIANT or DIRE. */
  private String team;

  /** PICK or FINAL. */
  private String stage;

  /** The findings, comma separated, such as {@code CARRIES_3,NO_INITIATOR}; empty for none. */
  @Column(length = TOKENS_LENGTH)
  private String tokens;

  /** The findings in plain words and the functions that could not be judged. */
  @Column(length = SUMMARY_LENGTH)
  private String summary;

  private Integer analysisVersion;
}
