package com.ako.dbuff.service.constant.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class AbilityIdsConstant implements ConstantData {
  Long id;
  String name;
}
