package com.ako.dbuff.service.match.report.analyzer.item;

import com.ako.dbuff.service.constant.ItemConstantService;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyFile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemEfficiencyConfigTest {

  private final ItemConstantService constants = mock(ItemConstantService.class);

  private ItemEfficiencyConfig config() {
    when(constants.getConstantMap())
        .thenReturn(
            Map.of(
                "hand_of_midas",
                ItemConstant.builder().id(65L).dname("Hand of Midas").build(),
                "octarine_core",
                ItemConstant.builder().id(235L).dname("Octarine Core").build()));
    ItemEfficiencyFile file = new ItemEfficiencyFile("replay/items-winstats-test.yaml");
    return new ItemEfficiencyConfig(file, constants);
  }

  @Test
  void groupsComeFromTheCatalogWinStatsAndAnUnknownKeySkipsItsGroup() {
    assertThat(config().getGroups())
        .extracting(
            ItemEfficiencyConfig.ItemGroup::getName, ItemEfficiencyConfig.ItemGroup::getItemIds)
        .containsExactly(
            tuple("Hand of Midas", List.of(65L)),
            tuple("Hand of Midas + Octarine Core", List.of(65L, 235L)));
  }
}
