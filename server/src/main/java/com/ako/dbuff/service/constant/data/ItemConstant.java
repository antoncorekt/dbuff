package com.ako.dbuff.service.constant.data;

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
}
