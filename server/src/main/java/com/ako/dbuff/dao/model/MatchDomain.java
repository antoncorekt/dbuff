package com.ako.dbuff.dao.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MatchDomain {

  @Id private Long id;

  private LocalDateTime startProcess;
  private LocalDateTime endProcess;

  private Long startTime;
  private Long startTimeMillis;
  private LocalDate startLocalDate;
  private int startMonth;
  private int startYear;
  private Long duration;
  private Long firstBloodTime;
  private Long radiantScore;
  private Long direScore;
  private Boolean radiantWin;
  private Long patch;
  private String patchStr;

  private String error;

  private String gameModeName;

  private Boolean dotaApiFailed;
  private Boolean abadon;
}
