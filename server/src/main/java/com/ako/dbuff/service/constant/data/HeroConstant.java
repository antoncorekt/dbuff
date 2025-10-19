package com.ako.dbuff.service.constant.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HeroConstant implements ConstantData {

  String id;
  String name;

  String localized_name;
}
