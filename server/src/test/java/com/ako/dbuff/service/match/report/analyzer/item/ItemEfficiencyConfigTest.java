package com.ako.dbuff.service.match.report.analyzer.item;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ItemEfficiencyConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(Binding.class);

  @EnableConfigurationProperties(ItemEfficiencyConfig.class)
  static class Binding {}

  @Test
  void everyGroupInApplicationProperties_isLoadedUnderItsOwnIndex() {
    runner.run(
        context -> {
          List<ItemEfficiencyConfig.ItemGroup> groups =
              context.getBean(ItemEfficiencyConfig.class).getGroups();

          assertThat(groups)
              .extracting(
                  ItemEfficiencyConfig.ItemGroup::getName,
                  ItemEfficiencyConfig.ItemGroup::getItemIds)
              .containsExactlyInAnyOrder(
                  tuple("Hand of Midas", List.of(65L)),
                  tuple("Hand of Midas + Octarine Core", List.of(65L, 235L)),
                  tuple("Magic Stick", List.of(34L)));
        });
  }
}
