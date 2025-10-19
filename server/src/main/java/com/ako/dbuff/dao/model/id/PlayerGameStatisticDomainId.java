package com.ako.dbuff.dao.model.id;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlayerGameStatisticDomainId implements Serializable {

  private Long playerId;

  private Long matchId;
}
