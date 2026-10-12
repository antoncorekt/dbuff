package com.ako.dbuff.service.replay.roleloop;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The review report of one stored match, as markdown. It only lays out what the tables hold; the
 * two tables headed {@link #ROLES} and {@link #VERDICTS} are what {@link RoleLoopDiff} compares
 * between rounds.
 */
final class RoleLoopReport {
  static final String ROLES = "Roles";
  static final String VERDICTS = "Verdicts";
  static final String NO_INTENT = "n/a";

  private static final List<String> TEAMS = List.of("RADIANT", "DIRE");
  private static final List<String> VERDICT_STAGES = List.of("PICK", "FINAL");
  private static final int FIRST_DIRE_SLOT = 5;

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      List<PlayerMatchStatisticDomain> stats,
      List<ReplayPlayerDomain> replayPlayers,
      List<PlayerRoleDomain> roles,
      List<PlayerRoleProfileDomain> profiles,
      List<TeamCompositionDomain> compositions,
      List<AbilityDraftPickDomain> picks) {}

  private RoleLoopReport() {}

  static String unavailable(long matchId, String reason) {
    return "# Match %d%n%nNot reported: %s%n".formatted(matchId, reason);
  }

  static String render(MatchData d) {
    List<String> out = new ArrayList<>();
    out.add(header(d));
    out.add("");
    boolean noProfiles = d.profiles().isEmpty() && d.roles().isEmpty();
    boolean noIntent = d.profiles().stream().noneMatch(p -> "INTENT".equals(p.getStage()));
    if (noProfiles) {
      out.add("**No roles stored (no resolver for this mode).**");
    } else if (noIntent) {
      out.add(
          "**No intent rows: this mode's resolver gives final roles only, so intent shows "
              + NO_INTENT
              + " and there is no PICK verdict.**");
    }
    out.add("");
    out.addAll(rolesTable(d));
    out.add("");
    out.addAll(verdictsTable(d));
    out.add("");
    out.add("## Players");
    for (String team : TEAMS) {
      for (PlayerMatchStatisticDomain stat : team(d, team)) {
        out.add("");
        out.addAll(player(d, team, stat));
      }
    }
    out.add("");
    out.add("## Team verdicts");
    for (String team : TEAMS) {
      for (String stage : VERDICT_STAGES) {
        out.add("");
        out.addAll(verdictText(d, team, stage));
      }
    }
    return String.join("\n", out) + "\n";
  }

  private static String header(MatchData d) {
    MatchDomain m = d.match();
    String date =
        m.getStartTime() == null
            ? "unknown date"
            : Instant.ofEpochSecond(m.getStartTime()).atZone(ZoneOffset.UTC).toLocalDate() + "";
    String winner =
        m.getRadiantWin() == null ? "unknown" : (m.getRadiantWin() ? "Radiant" : "Dire");
    return "# Match %d | %s | %s | Radiant %s : %s Dire | winner %s"
        .formatted(
            d.matchId(),
            d.modeName(),
            date,
            m.getRadiantScore() == null ? "?" : m.getRadiantScore(),
            m.getDireScore() == null ? "?" : m.getDireScore(),
            winner);
  }

  private static List<String> rolesTable(MatchData d) {
    List<String> rows =
        new ArrayList<>(List.of("## " + ROLES, "", "| slot | hero | intent | final |"));
    rows.add("|---|---|---|---|");
    for (String team : TEAMS) {
      for (PlayerMatchStatisticDomain stat : team(d, team)) {
        long slot = stat.getPlayerSlot();
        rows.add(
            "| %d | %s | %s | %s |"
                .formatted(
                    slot,
                    hero(d, stat),
                    stage(d, slot, "INTENT")
                        .map(p -> String.valueOf(p.getRole()))
                        .orElse(NO_INTENT),
                    finalRole(d, slot).map(String::valueOf).orElse("none")));
      }
    }
    return rows;
  }

  private static List<String> verdictsTable(MatchData d) {
    List<String> rows =
        new ArrayList<>(
            List.of("## " + VERDICTS, "", "| team | stage | tokens |", "|---|---|---|"));
    for (String team : TEAMS) {
      for (String stage : VERDICT_STAGES) {
        rows.add(
            "| %s | %s | %s |"
                .formatted(
                    team,
                    stage,
                    composition(d, team, stage)
                        .map(c -> blankAs(c.getTokens(), "no findings"))
                        .orElse("none stored")));
      }
    }
    return rows;
  }

  private static List<String> player(MatchData d, String team, PlayerMatchStatisticDomain stat) {
    long slot = stat.getPlayerSlot();
    List<String> out = new ArrayList<>();
    out.add("### %s slot %d, %s".formatted(team, slot, hero(d, stat)));
    out.add(
        "- intent: "
            + stage(d, slot, "INTENT")
                .map(RoleLoopReport::stageLine)
                .orElse(NO_INTENT + " (none stored)"));
    out.add(
        "- final: "
            + stage(d, slot, "FINAL")
                .map(RoleLoopReport::stageLine)
                .orElse(finalRole(d, slot).map(r -> "role " + r).orElse("none stored")));
    List<String> skills = skills(d, slot);
    if (!skills.isEmpty()) {
      out.add("- drafted skills: " + String.join(", ", skills));
    }
    out.add(
        "- OpenDota: position_est %s, lane_role %s, net worth rank %s of %d (net worth %s)"
            .formatted(
                orQuestion(stat.getPositionEst()),
                orQuestion(stat.getLaneRole()),
                netWorthRank(d, team, stat),
                team(d, team).size(),
                orQuestion(stat.getNetWorth())));
    return out;
  }

  private static String stageLine(PlayerRoleProfileDomain p) {
    return "role %s (confidence %s), fitRole %s, lane %s%n  - roleScores %s%n  - %s%n  - evidence: %s"
        .formatted(
            orQuestion(p.getRole()),
            number(p.getConfidence()),
            orQuestion(p.getFitRole()),
            p.getLane() == null ? "unknown" : p.getLane(),
            blankAs(p.getRoleScores(), "unknown"),
            functions(p),
            blankAs(p.getEvidence(), "none"));
  }

  private static String functions(PlayerRoleProfileDomain p) {
    Map<String, Double> values = new LinkedHashMap<>();
    values.put("farmPriority", p.getFarmPriority());
    values.put("jungle", p.getJungle());
    values.put("roaming", p.getRoaming());
    values.put("visionDuty", p.getVisionDuty());
    values.put("initiation", p.getInitiation());
    values.put("disable", p.getDisable());
    values.put("saveHeal", p.getSaveHeal());
    values.put("damage", p.getDamage());
    values.put("durability", p.getDurability());
    return "functions: "
        + values.entrySet().stream()
            .map(e -> e.getKey() + " " + (e.getValue() == null ? "unknown" : number(e.getValue())))
            .collect(Collectors.joining(", "));
  }

  private static List<String> verdictText(MatchData d, String team, String stage) {
    Optional<TeamCompositionDomain> c = composition(d, team, stage);
    List<String> out = new ArrayList<>();
    out.add("### %s %s".formatted(team, stage));
    if (c.isEmpty()) {
      out.add(
          "none stored"
              + ("PICK".equals(stage) ? " (needs intent rows for all five players)" : ""));
    } else {
      out.add("tokens: " + blankAs(c.get().getTokens(), "no findings"));
      out.add("summary: " + blankAs(c.get().getSummary(), "none"));
    }
    return out;
  }

  private static Optional<TeamCompositionDomain> composition(
      MatchData d, String team, String stage) {
    return d.compositions().stream()
        .filter(c -> team.equals(c.getTeam()) && stage.equals(c.getStage()))
        .findFirst();
  }

  private static Optional<PlayerRoleProfileDomain> stage(MatchData d, long slot, String stage) {
    return d.profiles().stream()
        .filter(p -> p.getPlayerSlot() == slot && stage.equals(p.getStage()))
        .findFirst();
  }

  private static Optional<Integer> finalRole(MatchData d, long slot) {
    return stage(d, slot, "FINAL")
        .map(PlayerRoleProfileDomain::getRole)
        .or(
            () ->
                d.roles().stream()
                    .filter(r -> r.getPlayerSlot() == slot)
                    .findFirst()
                    .map(PlayerRoleDomain::getRole));
  }

  private static List<String> skills(MatchData d, long slot) {
    return d.picks().stream()
        .filter(p -> p.getPlayerSlot() == slot)
        .sorted(Comparator.comparing(AbilityDraftPickDomain::getPickOrder))
        .map(p -> p.getAbilityName() != null ? p.getAbilityName() : "ability " + p.getAbilityId())
        .toList();
  }

  private static String netWorthRank(MatchData d, String team, PlayerMatchStatisticDomain stat) {
    if (stat.getNetWorth() == null) {
      return "?";
    }
    long higher =
        team(d, team).stream()
            .filter(s -> s.getNetWorth() != null && s.getNetWorth() > stat.getNetWorth())
            .count();
    return String.valueOf(higher + 1);
  }

  private static List<PlayerMatchStatisticDomain> team(MatchData d, String team) {
    boolean dire = "DIRE".equals(team);
    return d.stats().stream()
        .filter(s -> (s.getPlayerSlot() >= FIRST_DIRE_SLOT) == dire)
        .sorted(Comparator.comparing(PlayerMatchStatisticDomain::getPlayerSlot))
        .toList();
  }

  private static String hero(MatchData d, PlayerMatchStatisticDomain stat) {
    return d.replayPlayers().stream()
        .filter(
            p -> p.getPlayerSlot().equals(stat.getPlayerSlot()) && p.getHeroEntityName() != null)
        .findFirst()
        .map(ReplayPlayerDomain::getHeroEntityName)
        .orElseGet(
            () ->
                stat.getHeroName() != null
                    ? stat.getHeroName()
                    : "hero " + orQuestion(stat.getHeroId()));
  }

  private static String orQuestion(Object value) {
    return value == null ? "?" : String.valueOf(value);
  }

  private static String blankAs(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String number(Double value) {
    return value == null ? "?" : String.format(Locale.ROOT, "%.2f", value);
  }
}
