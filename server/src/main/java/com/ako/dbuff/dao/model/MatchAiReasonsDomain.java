package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The AI "top reasons" answer for the tracked team of a match, or a mark that a paid call failed.
 * An answer is reused while the facts, the language and the model are the same; every row, failed
 * or not, counts toward the per-match daily call limit.
 */
@Entity
@Table(
    name = "match_ai_reasons",
    indexes = @Index(name = "idx_match_ai_reasons_match", columnList = "matchId, createdAt"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchAiReasonsDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "match_ai_reasons_seq")
  @SequenceGenerator(
      name = "match_ai_reasons_seq",
      sequenceName = "match_ai_reasons_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** The tracked team, RADIANT or DIRE. */
  @Column(length = 10)
  private String side;

  @Column(length = 64)
  private String factsHash;

  /** The validated reasons as JSON; null for a failed call. */
  @Column(columnDefinition = "TEXT")
  private String reasonsJson;

  private String model;
  private Integer tokensUsed;
  private LocalDateTime createdAt;

  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private AiReasonsStatus status;
}
