package com.ako.dbuff.dao.model.id;

import java.io.Serializable;
import lombok.Data;

@Data
public class AbilityId implements Serializable {

  private Long playerId;

  private Long matchId;

  private Long abilityId;
}
