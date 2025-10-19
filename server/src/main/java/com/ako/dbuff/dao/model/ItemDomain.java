package com.ako.dbuff.dao.model;

import com.ako.dbuff.dao.model.id.ItemId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@IdClass(ItemId.class)
@Builder
@ToString
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemDomain {

  @Id private Long itemId;

  @Id private Long matchId;

  @Id private Long playerId;

  private String itemName;
  private String itemPrettyName;
  private Long itemPurchaseTime;

  private boolean isNeutral;
  private String neutralEnhancement;
}
