package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** How many units of one kind a player killed. */
@Entity
@Table(
    name = "unit_kill",
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "unitName"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitKillDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "unit_kill_seq")
  @SequenceGenerator(name = "unit_kill_seq", sequenceName = "unit_kill_seq", allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  private Long playerId;

  /** Internal unit name, such as a creep, a neutral or a hero. */
  private String unitName;

  private Long killCount;
}
