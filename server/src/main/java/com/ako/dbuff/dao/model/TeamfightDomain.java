package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** One teamfight as detected by the OpenDota parser. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "fightIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamfightDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "teamfight_seq")
  @SequenceGenerator(name = "teamfight_seq", sequenceName = "teamfight_seq", allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer fightIndex;
  private Long startTime;
  private Long endTime;
  private Long lastDeathTime;
  private Long deaths;
}
