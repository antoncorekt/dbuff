package com.ako.dbuff.service.match.report.analyzer.item;

import com.ako.dbuff.service.constant.ItemConstantService;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyEntry;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** The item groups whose win rates are reported, built from the {@code winStats} of the catalog. */
@Slf4j
@Component
public class ItemEfficiencyConfig {

  private final ItemEfficiencyFile file;
  private final ItemConstantService itemConstantService;
  private volatile List<ItemGroup> groups;

  public ItemEfficiencyConfig(ItemEfficiencyFile file, ItemConstantService itemConstantService) {
    this.file = file;
    this.itemConstantService = itemConstantService;
  }

  /** Cached only when every group was built; otherwise the next call tries again. */
  public List<ItemGroup> getGroups() {
    List<ItemGroup> built = groups;
    if (built != null) {
      return built;
    }
    synchronized (this) {
      if (groups != null) {
        return groups;
      }
      Map<String, ItemConstant> constants = itemConstantService.getConstantMap();
      List<ItemGroup> result = new ArrayList<>();
      boolean complete = constants != null && !constants.isEmpty();
      for (ItemEfficiencyEntry entry : file.entries()) {
        for (List<String> keys : entry.winStats()) {
          ItemGroup group = constants == null ? null : group(entry.key(), keys, constants);
          if (group == null) {
            complete = false;
          } else {
            result.add(group);
          }
        }
      }
      List<ItemGroup> copy = List.copyOf(result);
      if (complete) {
        groups = copy;
      }
      return copy;
    }
  }

  private static ItemGroup group(
      String entryKey, List<String> keys, Map<String, ItemConstant> constants) {
    List<String> names = new ArrayList<>();
    List<Long> ids = new ArrayList<>();
    for (String key : keys) {
      ItemConstant constant = constants.get(key);
      if (constant == null || constant.getId() == null) {
        log.warn(
            "Catalog entry '{}' winStats item '{}' is not in the item constants", entryKey, key);
        return null;
      }
      names.add(constant.getDname() == null ? key : constant.getDname());
      ids.add(constant.getId());
    }
    ItemGroup group = new ItemGroup();
    group.setName(String.join(" + ", names));
    group.setItemIds(List.copyOf(ids));
    return group;
  }

  @Data
  public static class ItemGroup {
    private String name;
    private List<Long> itemIds;
  }
}
