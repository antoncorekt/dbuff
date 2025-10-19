package com.ako.dbuff.service.constant.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchTypeConstant implements ConstantData {

  String id;
  String name;
  Boolean balanced;
}
