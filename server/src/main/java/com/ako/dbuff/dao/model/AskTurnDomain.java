package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One message of an ask conversation, stored as the model API's JSON so it replays unchanged. */
@Entity
@Table(
    name = "ask_turn",
    indexes = @Index(name = "ask_turn_thread_seq", columnList = "threadId, seq"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AskTurnDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ask_turn_seq")
  @SequenceGenerator(name = "ask_turn_seq", sequenceName = "ask_turn_seq", allocationSize = 50)
  private Long id;

  private String threadId;
  private int seq;
  private String role;

  @Column(columnDefinition = "TEXT")
  private String contentJson;

  /** True for the turn that carries a person's question, not for tool results. */
  private boolean userQuestion;

  private long inputTokens;
  private long outputTokens;
  private long cacheReadTokens;
  private Instant createdAt;
}
