package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConstantItemKits implements ItemKits {
  private final ConstantsManagers constants;
  private volatile IdKeys keys;

  @Override
  public Optional<ItemConstant> item(String key) {
    return Optional.ofNullable(constants.getItemConstantMap().get(key));
  }

  @Override
  public Optional<String> key(long id) {
    Map<String, ItemConstant> current = constants.getItemConstantMap();
    IdKeys known = keys;
    if (known == null || known.source() != current) {
      Map<Long, String> byId = new HashMap<>();
      current.forEach(
          (key, item) -> {
            if (item.getId() != null) {
              byId.putIfAbsent(item.getId(), key);
            }
          });
      known = new IdKeys(current, byId);
      keys = known;
    }
    return Optional.ofNullable(known.byId().get(id));
  }

  private record IdKeys(Map<String, ItemConstant> source, Map<Long, String> byId) {}
}
