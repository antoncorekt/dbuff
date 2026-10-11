package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The state of the replay pipeline for one match; one row per match and parser version. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "parserVersion"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayParseRunDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "replay_parse_run_seq")
  @SequenceGenerator(
      name = "replay_parse_run_seq",
      sequenceName = "replay_parse_run_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** Version of the extractor set; a new version makes a finished match eligible again. */
  private Integer parserVersion;

  @Enumerated(EnumType.STRING)
  private ReplayStatus status;

  private Integer attempts;

  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;

  /** A transient failure is not retried before this moment. */
  private LocalDateTime nextAttemptAt;

  /** A RUNNING row whose lease has passed was abandoned by a crash or restart. */
  private LocalDateTime leaseUntil;

  private Long replayBytes;
  private Long parseMillis;
  private String clarityVersion;

  /** Game clock start on the demo clock, in ticks; the replay's 0:00. */
  private Integer clockStartTick;

  @Column(length = 1000)
  private String error;
}
