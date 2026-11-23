package com.ako.dbuff.dao.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A Discord thread in which the ask feature holds a conversation. */
@Entity
@Table(name = "ask_thread")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AskThreadDomain {
  @Id private String threadId;
  private String instanceId;
  private String channelId;
  private String starterDiscordUserId;
  private Instant createdAt;
  private Instant lastActivityAt;
}
