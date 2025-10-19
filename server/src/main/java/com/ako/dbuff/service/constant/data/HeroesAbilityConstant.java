package com.ako.dbuff.service.constant.data;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeroesAbilityConstant implements ConstantData {

  List<String> abilities;
}
