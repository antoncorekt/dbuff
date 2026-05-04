package com.ako.dbuff.service.match.report.analyzer;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.service.match.report.MatchReportContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class OtherPlayerAnalyzer implements ReportAnalyzer {

  @Override
  public List<Report> analyze(MatchReportContext context) {
    Set<Long> focusPlayerIds = context.getFocusPlayerIds();
    List<Report> reports = new ArrayList<>();

    List<PlayerMatchStatisticDomain> otherPlayers =
        context.getPlayerStatistics().stream()
            .filter(s -> !focusPlayerIds.contains(s.getPlayerId()))
            .toList();

    for (PlayerMatchStatisticDomain stat : otherPlayers) {
      List<AbilityDomain> playerAbilities =
          context.getAbilities().stream()
              .filter(a -> stat.getPlayerSlot().equals(a.getPlayerSlot()))
              .sorted(
                  Comparator.comparingLong(
                      a -> -(a.getDamageDealt() != null ? a.getDamageDealt() : 0)))
              .toList();

      List<OtherPlayerReport.AbilitySummary> abilities =
          playerAbilities.stream()
              .map(
                  a ->
                      new OtherPlayerReport.AbilitySummary(
                          a.getPrettyName(), a.getDamageDealt(), a.getUseCount()))
              .toList();

      OtherPlayerReport report = new OtherPlayerReport();
      report.setPlayerId(stat.getPlayerId());
      report.setPlayerSlot(stat.getPlayerSlot());
      report.setHeroPrettyName(stat.getHeroPrettyName());
      report.setRadiant(stat.getIsRadiant() != null && stat.getIsRadiant());
      report.setAbilities(abilities);

      List<ItemDomain> playerItems =
          context.getItems().stream()
              .filter(i -> stat.getPlayerSlot().equals(i.getPlayerSlot()))
              .sorted(
                  Comparator.comparingLong(
                      i ->
                          i.getItemPurchaseTime() != null
                              ? i.getItemPurchaseTime()
                              : Long.MAX_VALUE))
              .toList();

      List<OtherPlayerReport.ItemSummary> items =
          playerItems.stream()
              .map(
                  i ->
                      new OtherPlayerReport.ItemSummary(
                          i.getItemPrettyName(), i.getItemPurchaseTime(), i.isNeutral()))
              .toList();

      report.setItems(items);
      report.setHeroDamage(val(stat.getHeroDamage()));
      report.setTowerDamage(val(stat.getTowerDamage()));
      report.setHeroHealing(val(stat.getHeroHealing()));
      report.setDamageTaken(val(stat.getDamageTaken()));

      reports.add(report);
    }

    return reports;
  }

  private static long val(Long v) {
    return v != null ? v : 0;
  }

  @Override
  public String getAnalyzerName() {
    return "OtherPlayers";
  }

  @Override
  public int getOrder() {
    return 10;
  }
}
