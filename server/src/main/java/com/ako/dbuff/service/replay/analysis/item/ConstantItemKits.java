package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConstantItemKits implements ItemKits {
  private final ConstantsManagers constants;

  @Override
  public Optional<ItemConstant> item(String key) {
    return Optional.ofNullable(constants.getItemConstantMap().get(key));
  }

  @Override
  public Optional<String> key(long id) {
    return constants.getItemConstantMap().entrySet().stream()
        .filter(e -> e.getValue().getId() != null && e.getValue().getId() == id)
        .map(Map.Entry::getKey)
        .findFirst();
  }
}
