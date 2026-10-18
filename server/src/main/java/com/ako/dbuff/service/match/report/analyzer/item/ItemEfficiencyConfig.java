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

  public List<ItemGroup> getGroups() {
    List<ItemGroup> built = groups;
    if (built == null) {
      synchronized (this) {
        if (groups == null) {
          groups = build();
        }
        built = groups;
      }
    }
    return built;
  }

  private List<ItemGroup> build() {
    Map<String, ItemConstant> constants = itemConstantService.getConstantMap();
    List<ItemGroup> result = new ArrayList<>();
    for (ItemEfficiencyEntry entry : file.entries()) {
      for (List<String> keys : entry.winStats()) {
        ItemGroup group = group(entry.key(), keys, constants);
        if (group != null) {
          result.add(group);
        }
      }
    }
    return List.copyOf(result);
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
