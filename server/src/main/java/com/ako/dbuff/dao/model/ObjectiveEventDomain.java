package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** One objective event: building kill, Roshan, Aegis, first blood, courier kill. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "objectiveIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectiveEventDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "objective_event_seq")
  @SequenceGenerator(
      name = "objective_event_seq",
      sequenceName = "objective_event_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** Order of the event within the match. */
  private Integer objectiveIndex;

  private Long eventTime;
  private String eventType;
  private String unit;

  /** Building name, or event specific key. */
  private String objectiveKey;

  private Long playerSlot;
  private Long victimPlayerSlot;

  /** 2 = Radiant, 3 = Dire where OpenDota provides it. */
  private Long team;

  private Long eventValue;
  private Long killer;
}
