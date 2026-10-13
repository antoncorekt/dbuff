package com.ako.dbuff.service.constant.data;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemConstant implements ConstantData {
  private Long id;
  private String dname;
  private Long cost;
  private List<Map<String, String>> attrib;

  /** Item keys of the recipe parts, without the recipe scroll; null for a basic item. */
  private List<String> components;

  private Boolean created;

  /** Cooldown in seconds; OpenDota sends false for an item without one, read as null. */
  @JsonDeserialize(using = LevelValuesDeserializer.class)
  private List<Double> cd;

  /** Mana cost; false for none, read as null. */
  @JsonDeserialize(using = LevelValuesDeserializer.class)
  private List<Double> mc;

  /** Charges of an item that has them (Hand of Midas has 1); false for none, read as null. */
  @JsonDeserialize(using = LevelValuesDeserializer.class)
  private List<Double> charges;

  public void setComponents(List<String> components) {
    this.components =
        components == null ? null : components.stream().filter(c -> !c.isBlank()).toList();
  }
}
