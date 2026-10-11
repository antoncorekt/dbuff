package com.ako.dbuff.dao.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Identity of one player in the replay, tied to the OpenDota player slot. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayPlayerDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "replay_player_seq")
  @SequenceGenerator(
      name = "replay_player_seq",
      sequenceName = "replay_player_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  private Integer replayPlayerId;
  private Integer teamNum;

  /** Null for an anonymous player. */
  private Long accountId;

  private String heroEntityName;
}
