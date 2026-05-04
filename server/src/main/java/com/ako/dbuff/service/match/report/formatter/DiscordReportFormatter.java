package com.ako.dbuff.service.match.report.formatter;

import com.ako.dbuff.service.match.report.analyzer.LinkReport;
import com.ako.dbuff.service.match.report.analyzer.OtherPlayerReport;
import com.ako.dbuff.service.match.report.analyzer.PerPlayerReport;
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.TextReport;
import com.ako.dbuff.service.match.report.analyzer.ability.AbilityHistoryStatReport;
import com.ako.dbuff.service.match.report.analyzer.ability.ChosenAbilityPlayerReport;
import com.ako.dbuff.service.match.report.analyzer.damage.DamageHistoryReport;
import com.ako.dbuff.service.match.report.analyzer.item.ChosenItemPlayerReport;
import com.ako.dbuff.service.match.report.analyzer.item.ItemEfficiencyReport;
import com.ako.dbuff.service.match.report.analyzer.item.ItemHistoryStatReport;
import com.ako.dbuff.service.match.report.analyzer.lane.LaneMatchupReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DiscordReportFormatter implements ReportFormatter {

  private static final int MSG_LIMIT = 2000;

  @Override
  public List<String> formatForDiscord(List<Report> reports, Map<Long, String> playerNames) {
    List<String> messages = new ArrayList<>();

    List<LinkReport> links = new ArrayList<>();
    List<LaneMatchupReport> lanes = new ArrayList<>();
    Map<Long, List<PerPlayerReport>> focusPlayerReports = new LinkedHashMap<>();
    List<OtherPlayerReport> otherPlayers = new ArrayList<>();
    List<TextReport> textReports = new ArrayList<>();

    for (Report report : reports) {
      if (report instanceof LinkReport link) {
        links.add(link);
      } else if (report instanceof LaneMatchupReport lane) {
        lanes.add(lane);
      } else if (report instanceof OtherPlayerReport other) {
        otherPlayers.add(other);
      } else if (report instanceof TextReport text) {
        textReports.add(text);
      } else if (report instanceof PerPlayerReport perPlayer) {
        focusPlayerReports
            .computeIfAbsent(perPlayer.getPlayerId(), k -> new ArrayList<>())
            .add(perPlayer);
      }
    }

    StringBuilder first = new StringBuilder();
    for (LinkReport link : links) {
      first.append(String.format("[%s](%s)\n", link.getLabel(), link.getUrl()));
    }
    for (LaneMatchupReport lane : lanes) {
      first.append(formatLaneMatchup(lane));
    }
    splitAndAdd(messages, first.toString());

    for (var entry : focusPlayerReports.entrySet()) {
      long playerId = entry.getKey();
      List<PerPlayerReport> playerReports = entry.getValue();

      String playerName = playerNames.getOrDefault(playerId, "Player " + playerId);
      String heroName = resolveHeroName(playerReports);

      StringBuilder sb = new StringBuilder();
      sb.append("## ").append(playerName).append(" — ").append(heroName).append("\n");

      formatDamageSection(sb, playerReports);
      formatAbilitySection(sb, playerReports);
      formatItemSection(sb, playerReports);
      formatItemEfficiencySection(sb, playerReports);

      splitAndAdd(messages, sb.toString());
    }

    StringBuilder others = new StringBuilder();
    others.append("### Other Players\n");
    for (OtherPlayerReport r : otherPlayers) {
      others.append(formatOtherPlayer(r));
    }
    splitAndAdd(messages, others.toString());

    for (TextReport text : textReports) {
      splitAndAdd(messages, text.getText() + "\n");
    }

    return messages;
  }

  private void formatDamageSection(StringBuilder sb, List<PerPlayerReport> reports) {
    for (PerPlayerReport r : reports) {
      if (r instanceof DamageHistoryReport d) {
        if (d.getHistoricalMatchCount() > 0) {
          sb.append("**Damage** vs avg (")
              .append(d.getHistoricalMatchCount())
              .append(" games, ")
              .append(d.getHistoryMonths())
              .append("mo):\n```\n");
          sb.append(
              String.format(
                  "Hero DMG:  %6d (avg %6.0f)\n", d.getCurrentHeroDamage(), d.getAvgHeroDamage()));
          sb.append(
              String.format(
                  "Tower DMG: %6d (avg %6.0f)\n",
                  d.getCurrentTowerDamage(), d.getAvgTowerDamage()));
          sb.append(
              String.format(
                  "Healing:   %6d (avg %6.0f)\n",
                  d.getCurrentHeroHealing(), d.getAvgHeroHealing()));
          sb.append(
              String.format(
                  "DMG Taken: %6d (avg %6.0f)\n",
                  d.getCurrentDamageTaken(), d.getAvgDamageTaken()));
        } else {
          sb.append("**Damage:**\n```\n");
          sb.append(String.format("Hero DMG:  %6d\n", d.getCurrentHeroDamage()));
          sb.append(String.format("Tower DMG: %6d\n", d.getCurrentTowerDamage()));
          sb.append(String.format("Healing:   %6d\n", d.getCurrentHeroHealing()));
          sb.append(String.format("DMG Taken: %6d\n", d.getCurrentDamageTaken()));
        }
        sb.append("```\n");
      }
    }
  }

  private void formatAbilitySection(StringBuilder sb, List<PerPlayerReport> reports) {
    List<String> lines = new ArrayList<>();

    for (PerPlayerReport r : reports) {
      if (r instanceof ChosenAbilityPlayerReport a && a.getTotalMatches() > 0) {
        String name = a.getPrettyAbilityName() != null ? a.getPrettyAbilityName() : "Ability";
        lines.add(
            String.format(
                "%-20s %dW/%dL (%.0f%%) %dmo",
                name,
                a.getWinCount(),
                a.getLoseCount(),
                a.getWinRate() * 100,
                a.getHistoryMonths()));
      }
    }

    for (PerPlayerReport r : reports) {
      if (r instanceof AbilityHistoryStatReport h && h.getHistoricalMatchCount() > 0) {
        String name = h.getPrettyAbilityName() != null ? h.getPrettyAbilityName() : "Ability";
        StringBuilder line = new StringBuilder();
        line.append(String.format("%-20s", name));
        if (h.getCurrentDamageDealt() > 0 || h.getAvgDamageDealt() > 0) {
          line.append(
              String.format(" DMG %d(avg %.0f)", h.getCurrentDamageDealt(), h.getAvgDamageDealt()));
        }
        if (h.getCurrentUseCount() > 0 || h.getAvgUseCount() > 0) {
          line.append(
              String.format(" Uses %d(avg %.0f)", h.getCurrentUseCount(), h.getAvgUseCount()));
        }
        lines.add(line.toString());
      }
    }

    if (!lines.isEmpty()) {
      sb.append("**Abilities:**\n```\n");
      for (String line : lines) {
        sb.append(line).append("\n");
      }
      sb.append("```\n");
    }
  }

  private void formatItemSection(StringBuilder sb, List<PerPlayerReport> reports) {
    List<String> lines = new ArrayList<>();

    for (PerPlayerReport r : reports) {
      if (r instanceof ChosenItemPlayerReport i && i.getTotalMatches() > 0) {
        String name = i.getPrettyItemName() != null ? i.getPrettyItemName() : "Item";
        String prefix = i.isNeutral() ? "*" : " ";
        String time = i.getPurchaseTime() != null ? formatTime(i.getPurchaseTime()) : "";
        lines.add(
            String.format(
                "%s%-20s %dW/%dL (%.0f%%) %s",
                prefix, name, i.getWinCount(), i.getLoseCount(), i.getWinRate() * 100, time));
      }
    }

    for (PerPlayerReport r : reports) {
      if (r instanceof ItemHistoryStatReport h
          && h.getHistoricalMatchCount() > 0
          && (h.getCurrentDamageDealt() > 0
              || h.getAvgDamageDealt() > 0
              || h.getCurrentUseCount() > 0
              || h.getAvgUseCount() > 0)) {
        String name = h.getPrettyItemName() != null ? h.getPrettyItemName() : "Item";
        StringBuilder line = new StringBuilder();
        line.append(String.format(" %-20s", name));
        if (h.getCurrentDamageDealt() > 0 || h.getAvgDamageDealt() > 0) {
          line.append(
              String.format(" DMG %d(avg %.0f)", h.getCurrentDamageDealt(), h.getAvgDamageDealt()));
        }
        if (h.getCurrentUseCount() > 0 || h.getAvgUseCount() > 0) {
          line.append(
              String.format(" Uses %d(avg %.0f)", h.getCurrentUseCount(), h.getAvgUseCount()));
        }
        lines.add(line.toString());
      }
    }

    if (!lines.isEmpty()) {
      sb.append("**Items:**\n```\n");
      for (String line : lines) {
        sb.append(line).append("\n");
      }
      sb.append("```\n");
    }
  }

  private void formatItemEfficiencySection(StringBuilder sb, List<PerPlayerReport> reports) {
    for (PerPlayerReport r : reports) {
      if (r instanceof ItemEfficiencyReport eff) {
        sb.append(String.format("**[%s]** ", eff.getGroupName()));
        if (eff.getTotalMatches() > 0) {
          sb.append(
              String.format(
                  "%dW/%dL (%.0f%%) over %d matches (%dmo)",
                  eff.getWinCount(),
                  eff.getLoseCount(),
                  eff.getWinRate() * 100,
                  eff.getTotalMatches(),
                  eff.getHistoryMonths()));
        } else {
          sb.append("first time");
        }
        sb.append("\n");

        if (!eff.getAvgPurchaseTimeByItem().isEmpty()
            || !eff.getCurrentPurchaseTimeByItem().isEmpty()) {
          sb.append("```\n");
          for (int i = 0; i < eff.getItemIds().size(); i++) {
            Long itemId = eff.getItemIds().get(i);
            String name =
                i < eff.getItemNames().size() ? eff.getItemNames().get(i) : "Item " + itemId;
            Long current = eff.getCurrentPurchaseTimeByItem().get(itemId);
            Double avg = eff.getAvgPurchaseTimeByItem().get(itemId);
            sb.append(String.format("  %-20s", name));
            if (current != null) {
              sb.append(String.format(" at %s", formatTime(current)));
            }
            if (avg != null) {
              sb.append(String.format(" (avg %s)", formatTime(avg.longValue())));
            }
            sb.append("\n");
          }
          sb.append("```\n");
        }
      }
    }
  }

  private String formatOtherPlayer(OtherPlayerReport r) {
    String hero = r.getHeroPrettyName() != null ? r.getHeroPrettyName() : "Unknown";
    String team = r.isRadiant() ? "Rad" : "Dire";

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("**%s** (%s)", hero, team));
    if (r.getHeroDamage() > 0 || r.getTowerDamage() > 0 || r.getHeroHealing() > 0) {
      sb.append(String.format(" DMG:%d Heal:%d", r.getHeroDamage(), r.getHeroHealing()));
    }
    sb.append("\n");

    if (r.getAbilities() != null && !r.getAbilities().isEmpty()) {
      for (OtherPlayerReport.AbilitySummary a : r.getAbilities()) {
        String name = a.getPrettyName() != null ? a.getPrettyName() : "?";
        sb.append(String.format("  %-20s", name));
        if (a.getDamageDealt() != null && a.getDamageDealt() > 0) {
          sb.append(String.format(" DMG:%d", a.getDamageDealt()));
        }
        if (a.getUseCount() != null && a.getUseCount() > 0) {
          sb.append(String.format(" Uses:%d", a.getUseCount()));
        }
        sb.append("\n");
      }
    }

    return sb.toString();
  }

  private String formatLaneMatchup(LaneMatchupReport r) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("**%s** — %s\n", r.getLaneName(), r.getLaneOutcome()));

    if (!r.getRadiantPlayers().isEmpty()) {
      sb.append("Rad: ");
      for (var p : r.getRadiantPlayers()) {
        String marker = p.isFocusPlayer() ? "★" : "";
        sb.append(
            String.format(
                "%s%s(K:%d D:%d CS:%d) ",
                marker,
                p.getHeroPrettyName(),
                p.getLaneKills(),
                p.getLaneDeaths(),
                p.getLastHitsAt10()));
      }
      sb.append("\n");
    }
    if (!r.getDirePlayers().isEmpty()) {
      sb.append("Dire: ");
      for (var p : r.getDirePlayers()) {
        String marker = p.isFocusPlayer() ? "★" : "";
        sb.append(
            String.format(
                "%s%s(K:%d D:%d CS:%d) ",
                marker,
                p.getHeroPrettyName(),
                p.getLaneKills(),
                p.getLaneDeaths(),
                p.getLastHitsAt10()));
      }
      sb.append("\n");
    }

    sb.append(
        String.format(
            "Gold@10: %d vs %d | XP@10: %d vs %d\n\n",
            r.getRadiantGoldAt10(), r.getDireGoldAt10(), r.getRadiantXpAt10(), r.getDireXpAt10()));

    return sb.toString();
  }

  private String resolveHeroName(List<PerPlayerReport> reports) {
    for (PerPlayerReport r : reports) {
      if (r instanceof DamageHistoryReport d && d.getHeroPrettyName() != null) {
        return d.getHeroPrettyName();
      }
      if (r instanceof ChosenAbilityPlayerReport a && a.getHeroPrettyName() != null) {
        return a.getHeroPrettyName();
      }
    }
    return "Unknown";
  }

  private void splitAndAdd(List<String> messages, String content) {
    if (content == null || content.isBlank()) {
      return;
    }
    content = content.strip();
    if (content.length() <= MSG_LIMIT) {
      messages.add(content);
      return;
    }

    String[] lines = content.split("\n");
    StringBuilder chunk = new StringBuilder();
    for (String line : lines) {
      if (chunk.length() + line.length() + 1 > MSG_LIMIT && !chunk.isEmpty()) {
        messages.add(chunk.toString().strip());
        chunk = new StringBuilder();
      }
      chunk.append(line).append("\n");
    }
    if (!chunk.isEmpty()) {
      messages.add(chunk.toString().strip());
    }
  }

  private static String formatTime(long seconds) {
    long m = seconds / 60;
    long s = Math.abs(seconds % 60);
    if (seconds < 0) {
      return String.format("-%d:%02d", Math.abs(m), s);
    }
    return String.format("%d:%02d", m, s);
  }
}
