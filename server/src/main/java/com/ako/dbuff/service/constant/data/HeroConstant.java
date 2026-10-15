package com.ako.dbuff.service.constant.data;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A hero as OpenDota describes it. Everything after {@code localized_name} is null for data stored
 * before the fields were kept.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HeroConstant implements ConstantData {

  String id;
  String name;

  String localized_name;

  String primary_attr;
  String attack_type;
  List<String> roles;

  Double base_health;
  Double base_health_regen;
  Double base_mana;
  Double base_mana_regen;
  Double base_armor;
  Double base_mr;
  Double base_attack_min;
  Double base_attack_max;
  Double base_str;
  Double base_agi;
  Double base_int;
  Double str_gain;
  Double agi_gain;
  Double int_gain;
  Double attack_range;
  Double projectile_speed;
  Double attack_rate;
  Double move_speed;

  /** CDN path of the portrait; null for data stored before version 2. */
  String img;

  public HeroConstant(String id, String name, String localized_name) {
    this.id = id;
    this.name = name;
    this.localized_name = localized_name;
  }
}
