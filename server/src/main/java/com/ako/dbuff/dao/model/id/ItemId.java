package com.ako.dbuff.dao.model.id;

import java.io.Serializable;
import lombok.Data;

@Data
public class ItemId implements Serializable {
  private Long itemId;
  private Long matchId;
  private Long playerId;
}
