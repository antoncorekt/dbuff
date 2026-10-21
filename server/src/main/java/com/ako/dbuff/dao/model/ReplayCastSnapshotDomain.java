package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
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

/** The hero positions around the catalogued skill casts of one match, as a compressed blob. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayCastSnapshotDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "replay_cast_snapshot_seq")
  @SequenceGenerator(
      name = "replay_cast_snapshot_seq",
      sequenceName = "replay_cast_snapshot_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** Layout of {@link #data}; see CastSnapshotCodec. */
  private Integer codecVersion;

  private Integer snapshotCount;

  @Column(columnDefinition = "bytea")
  private byte[] data;
}
