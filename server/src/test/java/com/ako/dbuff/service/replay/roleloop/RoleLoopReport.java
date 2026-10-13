package com.ako.dbuff.service.replay.roleloop;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
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
 * two tables headed {@link #ROLES} and {@link #VERDICTS} are what {@link RoleLoopTopic} lets the
 * loop compare between rounds. The verdict columns are left empty for the reviewers.
 */
final class RoleLoopReport {
  static final String ROLES = "Roles";
  static final String VERDICTS = "Verdicts";
  static final String NO_INTENT = "n/a";

  private static final List<String> TEAMS = List.of("RADIANT", "DIRE");
  private static final List<String> VERDICT_STAGES = List.of("PICK", "FINAL");
  private static final int FIRST_DIRE_SLOT = 5;
  private static final int ROLE_COUNT = 5;
  private static final String HOW_TO_READ =
      "\"Roles\" gives each player's role in the game (final) and the role the draft meant"
          + " (intent, n/a where the mode has none), then the roles that suited the player best by"
          + " score. Roles are the usual positions: 1 carry, 2 mid, 3 offlane, 4 soft support, 5"
          + " hard support. \"Verdicts\" says what each team lacked or had too much of, at the"
          + " pick and at the end. Write OK or REJECT in the assistant and owner columns.";

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      List<PlayerMatchStatisticDomain> stats,
      Map<Long, String> heroNames,
      List<PlayerRoleDomain> roles,
      List<PlayerRoleProfileDomain> profiles,
      List<TeamCompositionDomain> compositions,
      List<AbilityDraftPickDomain> picks) {}

  private RoleLoopReport() {}

  static String render(MatchData d) {
    List<String> out = new ArrayList<>();
    out.add(ReviewLoopReport.matchHeader(d.matchId(), d.modeName(), d.match()));
    out.add("");
    if (d.stats().isEmpty()) {
      out.add("**No player statistics stored.**");
      return String.join("\n", out) + "\n";
    }
    out.addAll(ReviewLoopReport.introduction(HOW_TO_READ, summary(d)));
    out.add("");
    boolean noProfiles = d.profiles().isEmpty() && d.roles().isEmpty();
    boolean noIntent = d.profiles().stream().noneMatch(p -> "INTENT".equals(p.getStage()));
    if (noProfiles) {
      out.add("**No roles stored (no resolver for this mode).**");
      out.add("");
    } else if (noIntent) {
      out.add(
          "**No intent rows: this mode's resolver gives final roles only, so intent shows "
              + NO_INTENT
              + " and there is no pick verdict.**");
      out.add("");
    }
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

  private static String summary(MatchData d) {
    long different =
        d.stats().stream()
            .filter(
                s -> {
                  Optional<Integer> intent =
                      stage(d, s.getPlayerSlot(), "INTENT").map(PlayerRoleProfileDomain::getRole);
                  Optional<Integer> last =
                      stage(d, s.getPlayerSlot(), "FINAL").map(PlayerRoleProfileDomain::getRole);
                  return intent.isPresent() && last.isPresent() && !intent.equals(last);
                })
            .count();
    long withFindings =
        d.compositions().stream()
            .filter(c -> c.getTokens() != null && !c.getTokens().isBlank())
            .count();
    boolean noIntent = d.profiles().stream().noneMatch(p -> "INTENT".equals(p.getStage()));
    return "%d players, %s; %d of %d team verdicts have findings."
        .formatted(
            d.stats().size(),
            noIntent
                ? "no draft intent in this mode"
                : different + " of them ended in another role than the draft meant",
            withFindings,
            d.compositions().size());
  }

  private static List<String> rolesTable(MatchData d) {
    List<String> rows =
        new ArrayList<>(
            List.of(
                "## " + ROLES,
                "",
                "| player | final role | intent role | final fit | intent fit | assistant | owner"
                    + " |",
                "|---|---|---|---|---|---|---|"));
    for (String team : TEAMS) {
      for (PlayerMatchStatisticDomain stat : team(d, team)) {
        long slot = stat.getPlayerSlot();
        rows.add(
            "| %s | %s | %s | %s | %s | | |"
                .formatted(
                    ReviewLoopReport.tableCell(playerLabel(d, team, slot)),
                    finalRole(d, slot).orElse("none"),
                    stage(d, slot, "INTENT")
                        .map(p -> LoopWording.role(p.getRole()))
                        .orElse(NO_INTENT),
                    fit(stage(d, slot, "FINAL")),
                    fit(stage(d, slot, "INTENT"))));
      }
    }
    return rows;
  }

  private static String playerLabel(MatchData d, String team, long slot) {
    return "%s (%s)".formatted(LoopWording.hero(d.heroNames(), slot), LoopWording.team(team));
  }

  /** The best scores of a stage, so a role left over by the assignment shows. */
  private static String fit(Optional<PlayerRoleProfileDomain> profile) {
    String scores = profile.map(PlayerRoleProfileDomain::getRoleScores).orElse(null);
    return scores == null || scores.isBlank() ? NO_INTENT : LoopWording.bestFits(scores);
  }

  private static String stageName(String stage) {
    return "PICK".equals(stage) ? "at the pick" : "final";
  }

  private static List<String> verdictsTable(MatchData d) {
    List<String> rows =
        new ArrayList<>(
            List.of(
                "## " + VERDICTS,
                "",
                "| team verdict | findings | assistant | owner |",
                "|---|---|---|---|"));
    for (String team : TEAMS) {
      for (String stage : VERDICT_STAGES) {
        rows.add(
            "| %s %s | %s | | |"
                .formatted(
                    LoopWording.team(team),
                    stageName(stage),
                    composition(d, team, stage)
                        .map(
                            c ->
                                ReviewLoopReport.tableCell(
                                    LoopWording.tokens(c.getTokens(), "no findings")))
                        .orElse("none stored")));
      }
    }
    return rows;
  }

  private static List<String> player(MatchData d, String team, PlayerMatchStatisticDomain stat) {
    long slot = stat.getPlayerSlot();
    List<String> out = new ArrayList<>();
    out.add("### %s".formatted(playerLabel(d, team, slot)));
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
        "- OpenDota: position %s, %s, net worth rank %s of %d (net worth %s)"
            .formatted(
                orQuestion(stat.getPositionEst()),
                LoopWording.laneRole(stat.getLaneRole()),
                netWorthRank(d, team, stat),
                team(d, team).size(),
                LoopWording.gold(stat.getNetWorth())));
    return out;
  }

  private static String stageLine(PlayerRoleProfileDomain p) {
    return "role %s (confidence %s), best fit %s, lane %s%n  - scores, best first: %s%n  - %s%n  - evidence: %s"
        .formatted(
            LoopWording.role(p.getRole()),
            number(p.getConfidence()),
            LoopWording.role(p.getFitRole()),
            p.getLane() == null ? "unknown" : LoopWording.words(p.getLane()),
            LoopWording.bestFits(p.getRoleScores(), ROLE_COUNT),
            functions(p),
            blankAs(p.getEvidence(), "none"));
  }

  private static String functions(PlayerRoleProfileDomain p) {
    Map<String, Double> values = new LinkedHashMap<>();
    values.put("farm priority", p.getFarmPriority());
    values.put("jungle", p.getJungle());
    values.put("roaming", p.getRoaming());
    values.put("vision duty", p.getVisionDuty());
    values.put("initiation", p.getInitiation());
    values.put("disable", p.getDisable());
    values.put("save or heal", p.getSaveHeal());
    values.put("damage", p.getDamage());
    values.put("durability", p.getDurability());
    return "what the hero does: "
        + values.entrySet().stream()
            .map(e -> e.getKey() + " " + (e.getValue() == null ? "unknown" : number(e.getValue())))
            .collect(Collectors.joining(", "));
  }

  private static List<String> verdictText(MatchData d, String team, String stage) {
    Optional<TeamCompositionDomain> c = composition(d, team, stage);
    List<String> out = new ArrayList<>();
    out.add("### %s %s".formatted(LoopWording.team(team), stageName(stage)));
    if (c.isEmpty()) {
      out.add(
          "none stored"
              + ("PICK".equals(stage) ? " (needs intent rows for all five players)" : ""));
    } else {
      out.add("findings: " + LoopWording.tokens(c.get().getTokens(), "no findings"));
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

  private static Optional<String> finalRole(MatchData d, long slot) {
    return stage(d, slot, "FINAL")
        .map(p -> LoopWording.role(p.getRole()))
        .or(
            () ->
                d.roles().stream()
                    .filter(r -> r.getPlayerSlot() == slot)
                    .findFirst()
                    .map(r -> LoopWording.role(r.getRole())));
  }

  private static List<String> skills(MatchData d, long slot) {
    return d.picks().stream()
        .filter(p -> p.getPlayerSlot() == slot)
        .sorted(Comparator.comparing(AbilityDraftPickDomain::getPickOrder))
        .map(
            p ->
                p.getAbilityName() != null
                    ? LoopWording.ability(p.getAbilityName())
                    : "ability " + p.getAbilityId())
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
