package com.ako.dbuff.service.match.report.analyzer;

import com.ako.dbuff.service.match.report.analyzer.ability.AbilityHistoryStatReport;
import com.ako.dbuff.service.match.report.analyzer.ability.ChosenAbilityPlayerReport;
import com.ako.dbuff.service.match.report.analyzer.damage.DamageHistoryReport;
import com.ako.dbuff.service.match.report.analyzer.item.ChosenItemPlayerReport;
import com.ako.dbuff.service.match.report.analyzer.item.ItemEfficiencyReport;
import com.ako.dbuff.service.match.report.analyzer.item.ItemHistoryStatReport;
import com.ako.dbuff.service.match.report.analyzer.lane.LaneMatchupReport;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = TextReport.class, name = "text"),
  @JsonSubTypes.Type(value = LinkReport.class, name = "link"),
  @JsonSubTypes.Type(value = LaneMatchupReport.class, name = "laneMatchup"),
  @JsonSubTypes.Type(value = ChosenAbilityPlayerReport.class, name = "chosenAbility"),
  @JsonSubTypes.Type(value = AbilityHistoryStatReport.class, name = "abilityHistoryStats"),
  @JsonSubTypes.Type(value = ChosenItemPlayerReport.class, name = "chosenItem"),
  @JsonSubTypes.Type(value = ItemHistoryStatReport.class, name = "itemHistoryStats"),
  @JsonSubTypes.Type(value = ItemEfficiencyReport.class, name = "itemEfficiency"),
  @JsonSubTypes.Type(value = DamageHistoryReport.class, name = "damageHistory"),
  @JsonSubTypes.Type(value = OtherPlayerReport.class, name = "otherPlayer"),
})
public interface Report {

  String getName();
}
