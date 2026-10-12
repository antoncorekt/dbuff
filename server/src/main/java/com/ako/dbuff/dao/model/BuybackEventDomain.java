package com.ako.dbuff.dao.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One buyback, with the death it followed and how the fight of that death went for the team. */
@Entity
@Table(indexes = @Index(name = "idx_buyback_event_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuybackEventDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "buyback_event_seq")
  @SequenceGenerator(
      name = "buyback_event_seq",
      sequenceName = "buyback_event_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** Seconds from 0:00 at which the buyback was paid for. */
  private Double seconds;

  /** When the hero died before it; null when the combat log has no such death. */
  private Double deathSeconds;

  /** How long he was dead before buying back; null without a death. */
  private Double secondsDead;

  /** The fight the death belongs to; null outside any fight. */
  private Integer fightIndex;

  /** Whether the buyer's team won that fight; null for a draw, or without a fight. */
  private Boolean fightWon;

  private Integer analysisVersion;
}
