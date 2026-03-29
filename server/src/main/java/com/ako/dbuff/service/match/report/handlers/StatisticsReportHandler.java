package com.ako.dbuff.service.match.report.handlers;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.KillLogDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.service.match.report.MatchReportContext;
import com.ako.dbuff.service.match.report.MatchReportHandler;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StatisticsReportHandler implements MatchReportHandler {

  private static final int DISCORD_MESSAGE_LIMIT = 2000;

  @Override
  public String handle(MatchReportContext context) {
    MatchDomain match = context.getMatch();
    Set<Long> focusPlayerIds = context.getFocusPlayerIds();
    Map<Long, String> playerNames = context.getPlayerNames();

    List<PlayerMatchStatisticDomain> focusStats =
        context.getPlayerStatistics().stream()
            .filter(s -> focusPlayerIds.contains(s.getPlayerId()))
            .toList();

    if (focusStats.isEmpty()) {
      return "No tracked player data found for match " + match.getId() + ".";
    }

    StringBuilder sb = new StringBuilder();

    for (PlayerMatchStatisticDomain stat : focusStats) {
      String name = resolvePlayerName(stat, playerNames);
      String hero = stat.getHeroPrettyName() != null ? stat.getHeroPrettyName() : "Unknown Hero";
      String winLoss = stat.getWin() != null && stat.getWin() == 1 ? "WIN" : "LOSS";

      sb.append("## ").append(name).append(" - ").append(hero);
      sb.append(" [").append(winLoss).append("]\n");

      // Core stats
      sb.append("```\n");
      sb.append(String.format("%-10s %d/%d/%d", "KDA:", val(stat.getKills()), val(stat.getDeaths()), val(stat.getAssists())));
      if (stat.getKda() != null) {
        sb.append(String.format("  (%.1f)", stat.getKda()));
      }
      sb.append("\n");
      sb.append(String.format("%-10s %-8d %-10s %d\n", "GPM:", val(stat.getGoldPerMin()), "XPM:", val(stat.getXpPerMin())));
      sb.append(String.format("%-10s %-8d %-10s %d\n", "Last Hits:", val(stat.getLastHits()), "Denies:", val(stat.getDenies())));
      sb.append(String.format("%-10s %-8d %-10s %d\n", "Net Worth:", val(stat.getNetWorth()), "Level:", val(stat.getLevel())));
      sb.append(String.format("%-10s %-8d %-10s %d\n", "Hero DMG:", val(stat.getHeroDamage()), "Tower DMG:", val(stat.getTowerDamage())));
      sb.append(String.format("%-10s %-8d %-10s %d\n", "Healing:", val(stat.getHeroHealing()), "DMG Taken:", val(stat.getDamageTaken())));

      // Support stats
      if (val(stat.getObsPlaced()) > 0 || val(stat.getSenPlaced()) > 0 || val(stat.getCampsStacked()) > 0) {
        sb.append(String.format("%-10s %-8d %-10s %-8d %-10s %d\n", "Obs:", val(stat.getObsPlaced()), "Sen:", val(stat.getSenPlaced()), "Stacks:", val(stat.getCampsStacked())));
      }

      // Lane info
      if (stat.getLaneRole() != null) {
        sb.append(String.format("%-10s %-8s", "Lane:", laneRoleName(stat.getLaneRole())));
        if (stat.getLaneEfficiencyPct() != null) {
          sb.append(String.format(" %-10s %.0f%%", "Eff:", stat.getLaneEfficiencyPct()));
        }
        sb.append("\n");
      }

      // Teamfight & APM
      if (stat.getTeamfightParticipation() != null || stat.getActionsPerMin() != null) {
        if (stat.getTeamfightParticipation() != null) {
          sb.append(String.format("%-10s %.0f%%", "TF Part:", stat.getTeamfightParticipation().multiply(java.math.BigDecimal.valueOf(100))));
        }
        if (stat.getActionsPerMin() != null) {
          sb.append(String.format("   %-10s %d", "APM:", stat.getActionsPerMin()));
        }
        sb.append("\n");
      }
      sb.append("```\n");

      // Kill log
      appendKillLog(sb, context.getKillLogs(), stat.getPlayerSlot(), stat.getPlayerId(), focusStats);

      // Abilities
      appendAbilities(sb, context.getAbilities(), stat.getPlayerSlot());

      // Items
      appendItems(sb, context.getItems(), stat.getPlayerSlot());

      sb.append("\n");
    }

    String result = sb.toString();
    if (result.length() > DISCORD_MESSAGE_LIMIT) {
      result = result.substring(0, DISCORD_MESSAGE_LIMIT - 3) + "...";
    }
    return result;
  }

  private void appendKillLog(
      StringBuilder sb,
      List<KillLogDomain> allKillLogs,
      Long playerSlot,
      Long playerId,
      List<PlayerMatchStatisticDomain> focusStats) {

    List<KillLogDomain> kills =
        allKillLogs.stream().filter(k -> playerSlot.equals(k.getPlayerSlot())).toList();
    List<KillLogDomain> deaths =
        allKillLogs.stream().filter(k -> playerSlot.equals(k.getKilledPlayerSlot())).toList();

    if (kills.isEmpty() && deaths.isEmpty()) {
      return;
    }

    sb.append("**Kill Log:**\n```\n");
    if (!kills.isEmpty()) {
      // Count kills per hero
      Map<String, Long> killCounts = kills.stream()
          .collect(java.util.stream.Collectors.groupingBy(
              k -> k.getKilledHeroPrettyName() != null ? k.getKilledHeroPrettyName() : k.getKilledHeroName(),
              java.util.stream.Collectors.counting()));

      sb.append("Killed: ");
      killCounts.entrySet().stream()
          .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
          .forEach(e -> sb.append(e.getKey()).append(" x").append(e.getValue()).append(", "));
      sb.setLength(sb.length() - 2);
      sb.append("\n");
    }
    if (!deaths.isEmpty()) {
      Map<String, Long> deathCounts = deaths.stream()
          .collect(java.util.stream.Collectors.groupingBy(
              k -> {
                // Find killer hero name from player stats
                return focusStats.stream()
                    .filter(s -> s.getPlayerSlot().equals(k.getPlayerSlot()))
                    .map(s -> s.getHeroPrettyName() != null ? s.getHeroPrettyName() : "Unknown")
                    .findFirst()
                    .orElse("Enemy");
              },
              java.util.stream.Collectors.counting()));

      sb.append("Died to: ");
      deathCounts.entrySet().stream()
          .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
          .forEach(e -> sb.append(e.getKey()).append(" x").append(e.getValue()).append(", "));
      sb.setLength(sb.length() - 2);
      sb.append("\n");
    }
    sb.append("```\n");
  }

  private void appendAbilities(
      StringBuilder sb, List<AbilityDomain> allAbilities, Long playerSlot) {

    List<AbilityDomain> playerAbilities =
        allAbilities.stream()
            .filter(a -> playerSlot.equals(a.getPlayerSlot()))
            .sorted(Comparator.comparingLong(a -> -val(a.getDamageDealt())))
            .toList();

    if (playerAbilities.isEmpty()) {
      return;
    }

    sb.append("**Abilities:**\n```\n");
    sb.append(String.format("%-24s %8s %8s %6s\n", "Name", "DMG Deal", "DMG Recv", "Uses"));
    sb.append("-".repeat(50)).append("\n");
    for (AbilityDomain ability : playerAbilities) {
      String name = ability.getPrettyName() != null ? ability.getPrettyName() : ability.getName();
      if (name != null && name.length() > 24) {
        name = name.substring(0, 23) + ".";
      }
      sb.append(String.format("%-24s %8s %8s %6s\n",
          name != null ? name : "?",
          fmtOpt(ability.getDamageDealt()),
          fmtOpt(ability.getDamageReceived()),
          fmtOpt(ability.getUseCount())));
    }
    sb.append("```\n");
  }

  private void appendItems(StringBuilder sb, List<ItemDomain> allItems, Long playerSlot) {

    List<ItemDomain> playerItems =
        allItems.stream()
            .filter(i -> playerSlot.equals(i.getPlayerSlot()))
            .sorted(Comparator.comparingLong(i -> i.getItemPurchaseTime() != null ? i.getItemPurchaseTime() : Long.MAX_VALUE))
            .toList();

    if (playerItems.isEmpty()) {
      return;
    }

    sb.append("**Items:**\n```\n");
    sb.append(String.format("%-22s %7s %8s %8s %6s\n", "Name", "Time", "DMG Deal", "DMG Recv", "Uses"));
    sb.append("-".repeat(55)).append("\n");
    for (ItemDomain item : playerItems) {
      String name = item.getItemPrettyName() != null ? item.getItemPrettyName() : item.getItemName();
      if (name != null && name.length() > 22) {
        name = name.substring(0, 21) + ".";
      }
      String time = item.getItemPurchaseTime() != null ? formatTime(item.getItemPurchaseTime()) : "-";
      String prefix = item.isNeutral() ? "*" : " ";
      sb.append(String.format("%s%-21s %7s %8s %8s %6s\n",
          prefix,
          name != null ? name : "?",
          time,
          fmtOpt(item.getDamageDealt()),
          fmtOpt(item.getDamageReceived()),
          fmtOpt(item.getUseCount())));
    }
    sb.append("```\n");
  }

  private String resolvePlayerName(PlayerMatchStatisticDomain stat, Map<Long, String> playerNames) {
    if (playerNames != null && stat.getPlayerId() != null) {
      String name = playerNames.get(stat.getPlayerId());
      if (name != null) {
        return name;
      }
    }
    return "Player " + (stat.getPlayerId() != null ? stat.getPlayerId() : stat.getPlayerSlot());
  }

  private static long val(Long v) {
    return v != null ? v : 0;
  }

  private static String fmtOpt(Long v) {
    return v != null && v != 0 ? String.valueOf(v) : "-";
  }

  private static String formatTime(long seconds) {
    long m = seconds / 60;
    long s = seconds % 60;
    return String.format("%d:%02d", m, s);
  }

  private static String laneRoleName(Long role) {
    if (role == null) return "?";
    return switch (role.intValue()) {
      case 1 -> "Safe";
      case 2 -> "Mid";
      case 3 -> "Off";
      case 4 -> "Jungle";
      default -> "Lane " + role;
    };
  }

  @Override
  public int getOrder() {
    return 1;
  }

  @Override
  public String getHandlerName() {
    return "Statistics";
  }
}
