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

/**
 * What one team could see around one important fight: its wards near the fight when it began, the
 * enemy's invisible heroes, its detection, and the verdict the rules draw. Wards count only when
 * they stand within their vision radius of the fight's centre; tower, creep and hero vision are not
 * counted.
 */
@Entity
@Table(
    name = "fight_vision",
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "fightIndex", "team"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FightVisionDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fight_vision_seq")
  @SequenceGenerator(
      name = "fight_vision_seq",
      sequenceName = "fight_vision_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer fightIndex;

  /** RADIANT or DIRE. */
  private String team;

  /**
   * The analysed window in game seconds: the fight's start less the lead to its end plus the tail.
   */
  private Double windowStart;

  private Double windowEnd;

  /** The team's observers alive at the fight's start within their radius of its centre. */
  private Integer activeObservers;

  /** The team's sentries alive at the fight's start within their radius of its centre. */
  private Integer activeSentries;

  /** Seconds from placement to the fight's start of the youngest and oldest of those observers. */
  private Integer youngestObserverAge;

  private Integer oldestObserverAge;

  /** The observers counted, a late one at its reduced weight. */
  private Double visionWeight;

  /** An observer near the fight was placed shortly before it began. */
  private boolean lateWard;

  /** Seconds before the fight's start that the latest of the late observers was placed. */
  private Integer lateWardSeconds;

  /** Enemy wards this team destroyed during the window, anywhere on the map. */
  private Integer wardsDestroyed;

  /** Internal names of the heroes that destroyed them, comma separated. */
  private String destroyedBy;

  /** An enemy hero in the fight was invisible during it. */
  private boolean invisibleHero;

  /** Internal names of those enemy heroes, comma separated. */
  private String invisibleHeroes;

  /** The team had a way to see invisible heroes: a sentry near, dust or a gem on a hero present. */
  private boolean detection;

  /** The team used dust or placed a sentry near the fight around its start. */
  private boolean detectionUsed;

  /** The verdict codes in the order they apply, comma separated; FINE when none applies. */
  private String verdict;

  private Integer analysisVersion;
}
