package com.ako.dbuff.dao.model;

import com.ako.dbuff.dao.model.id.AbilityId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@IdClass(AbilityId.class)
@Builder
@ToString
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbilityDomain {

  @Id private Long playerId;

  @Id private Long matchId;

  @Id private Long abilityId;

  private String name;
  private String prettyName;
}
