package com.ako.dbuff.service.constant.data;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An ability as OpenDota describes it. Only {@code dname} is guaranteed; the rest is null for data
 * stored before the fields were kept. Per-level values ({@code cd}, {@code mc}, {@code dmg}) are
 * listed by level, a single value as a one-element list.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AbilityConstant implements ConstantData {

  String dname;

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> behavior;

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> dmg_type;

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> bkbpierce;

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> dispellable;

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> target_team;

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> target_type;

  @JsonDeserialize(using = LevelValuesDeserializer.class)
  List<Double> cd;

  @JsonDeserialize(using = LevelValuesDeserializer.class)
  List<Double> mc;

  @JsonDeserialize(using = LevelValuesDeserializer.class)
  List<Double> dmg;

  Boolean is_innate;

  List<AbilityAttribute> attrib;

  /** CDN path of the icon; absent for talents. */
  String img;
}
