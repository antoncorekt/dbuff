package com.ako.dbuff.service.match.report.analyzer.item;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "dbuff.report.item-efficiency")
public class ItemEfficiencyConfig {

  private List<ItemGroup> groups = List.of();

  @Data
  public static class ItemGroup {
    private String name;
    private List<Long> itemIds;
  }
}
