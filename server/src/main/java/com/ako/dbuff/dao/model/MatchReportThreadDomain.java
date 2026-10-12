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

/**
 * The Discord thread a channel's report of a match was posted in, kept so that something that
 * arrives later, such as the replay analysis, can find it again.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "instanceId"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchReportThreadDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "match_report_thread_seq")
  @SequenceGenerator(
      name = "match_report_thread_seq",
      sequenceName = "match_report_thread_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private String instanceId;
  private String channelId;
  private Long threadId;
  private Long headerMessageId;
  private LocalDateTime createdAt;
}
