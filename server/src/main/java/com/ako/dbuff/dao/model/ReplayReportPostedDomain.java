package com.ako.dbuff.dao.model;

import jakarta.persistence.Entity;
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

/** Says that a channel has been dealt with for a match's replay analysis, so it is posted once. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "instanceId"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayReportPostedDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "replay_report_posted_seq")
  @SequenceGenerator(
      name = "replay_report_posted_seq",
      sequenceName = "replay_report_posted_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private String instanceId;

  /** How many messages went out; 0 when there was nothing to say. */
  private Integer messages;

  private LocalDateTime postedAt;
}
