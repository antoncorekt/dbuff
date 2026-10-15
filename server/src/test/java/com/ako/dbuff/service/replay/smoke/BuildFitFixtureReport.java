package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.BuildFitDomain;
import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.repo.BuildFitRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.service.replay.analysis.buildfit.BuildFitStage;
import com.ako.dbuff.service.replay.analysis.buildfit.BuildFitWording;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Writes what the build fit analyzer stored for a real match into {@code build-fit.md} of the send
 * folder, one section per match, replacing the section of the same match from an earlier run.
 */
record BuildFitFixtureReport(
    BuildFitRepo buildFitRepo,
    PlayerRoleProfileRepo profileRepo,
    PlayerGameStatisticRepo statisticRepo,
    DeathAnalysisRepo deathRepo,
    HeroKits heroKits) {
  private static final String HEADER = "## Match ";

  void write(String id, long matchId, String mode) {
    List<BuildFitDomain> rows = buildFitRepo.findByMatchId(matchId);
    StringBuilder section = new StringBuilder(HEADER + id + " (" + mode + ")\n\n");
    if (rows.isEmpty()) {
      section.append("No build verdicts stored: build fit is judged for Ability Draft only.\n");
    } else {
      render(section, matchId, rows);
    }
    merge(id, section.toString());
  }

  private void render(StringBuilder out, long matchId, List<BuildFitDomain> rows) {
    List<PlayerRoleProfileDomain> profiles = profileRepo.findByMatchId(matchId);
    Map<Long, Long> lonely =
        deathRepo.findByMatchId(matchId).stream()
            .filter(d -> Boolean.TRUE.equals(d.getIsolated()))
            .collect(
                Collectors.groupingBy(DeathAnalysisDomain::getPlayerSlot, Collectors.counting()));
    for (PlayerMatchStatisticDomain stat :
        statisticRepo.findAllByMatchId(matchId).stream()
            .sorted(Comparator.comparing(PlayerMatchStatisticDomain::getPlayerSlot))
            .toList()) {
      long slot = stat.getPlayerSlot();
      List<BuildFitDomain> own =
          rows.stream().filter(r -> r.getPlayerSlot() == slot).sorted(stageOrder()).toList();
      if (own.isEmpty()) {
        continue;
      }
      String hero =
          heroKits
              .hero(stat.getHeroId())
              .map(h -> h.getLocalized_name())
              .orElse("Hero " + stat.getHeroId());
      out.append("### ").append(hero).append(slot >= 128 ? " (Dire)" : " (Radiant)").append("\n");
      long alone = lonely.getOrDefault(slot, 0L);
      out.append("- Net worth ")
          .append(thousands(stat.getNetWorth()))
          .append(", died alone ")
          .append(alone)
          .append(alone == 1 ? " time\n" : " times\n");
      profiles.stream()
          .filter(p -> p.getPlayerSlot() == slot && "INTENT".equals(p.getStage()))
          .findFirst()
          .ifPresent(p -> out.append("- Drafted build: ").append(functions(p)).append("\n"));
      if (own.size() == 2) {
        out.append("- ")
            .append(BuildFitWording.roleChange(hero, own.get(0).getRole(), own.get(1).getRole()))
            .append("\n");
      }
      for (BuildFitDomain r : own) {
        out.append("- ").append(r.getEvidence()).append("\n");
        out.append("  - missing: ")
            .append(blank(r.getMissing()))
            .append("; backed by the match: ")
            .append(blank(r.getStated()))
            .append("\n");
      }
      out.append("\n");
    }
  }

  private static Comparator<BuildFitDomain> stageOrder() {
    return Comparator.comparing(r -> BuildFitStage.valueOf(r.getStage()));
  }

  private static String functions(PlayerRoleProfileDomain p) {
    Map<String, Double> values = new TreeMap<>();
    values.put("farm", p.getFarmPriority());
    values.put("damage", p.getDamage());
    values.put("disable", p.getDisable());
    values.put("initiation", p.getInitiation());
    values.put("save and heal", p.getSaveHeal());
    values.put("durability", p.getDurability());
    values.put("escape", p.getEscape());
    return values.entrySet().stream()
        .map(
            e ->
                e.getKey()
                    + " "
                    + (e.getValue() == null
                        ? "unknown"
                        : String.format(Locale.ROOT, "%.2f", e.getValue())))
        .collect(Collectors.joining(", "));
  }

  private static String thousands(Long worth) {
    return worth == null ? "unknown" : String.format(Locale.ROOT, "%.1fk", worth / 1000.0);
  }

  private static String blank(String text) {
    return text == null || text.isBlank()
        ? "none"
        : Arrays.stream(text.split(","))
            .map(name -> RoleFunction.valueOf(name).words())
            .collect(Collectors.joining(", "));
  }

  private static void merge(String id, String section) {
    Path file = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send", "build-fit.md");
    try {
      Files.createDirectories(file.getParent());
      Map<String, String> sections = new TreeMap<>();
      if (Files.exists(file)) {
        for (String block : Files.readString(file).split("(?m)^(?=## Match )")) {
          if (block.startsWith(HEADER)) {
            sections.put(block.substring(HEADER.length()).split("[ \n]", 2)[0], block);
          }
        }
      }
      sections.put(id, section);
      String text =
          "# Build fit of the four fixture matches\n\n"
              + "Thresholds are first guesses (hypotheses). A gap is stated only when the match"
              + " backs it.\n\n"
              + String.join("\n", sections.values());
      Files.writeString(file, text);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
