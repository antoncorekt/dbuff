package com.ako.dbuff.service.constant.data;

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
}
