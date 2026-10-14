package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.replay.analysis.composition.CompositionStage;
import com.ako.dbuff.service.replay.analysis.role.RoleStage;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The role each tracked player was meant to play and played, and what their team's composition
 * lacked, at the draft and in the game. A mode without an intent stage (All Pick) has no intent or
 * draft lines, because none is stored.
 */
@Component
@RequiredArgsConstructor
public class CompositionSection implements ReplayReportSection {
  private static final String ELLIPSIS = "...";
  private static final Map<Integer, String> ROLES =
      Map.of(
          1, "carry",
          2, "mid",
          3, "offlane",
          4, "roaming support",
          5, "hard support");
  private static final Map<CompositionStage, String> STAGE_LABELS =
      Map.of(CompositionStage.PICK, "at the draft", CompositionStage.FINAL, "in the game");

  private final PlayerRoleProfileRepo profileRepo;
  private final TeamCompositionRepo compositionRepo;
  private final ReplayReportProperties properties;

  @Override
  public String name() {
    return "composition";
  }

  @Override
  public int order() {
    return 12;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<PlayerRoleProfileDomain> profiles = profileRepo.findByMatchId(input.matchId());
    List<String> lines = new ArrayList<>();
    int listed = 0;
    for (Map.Entry<Long, String> player : input.namesBySlot().entrySet()) {
      String roles = roles(profiles, player.getKey());
      if (roles == null) {
        continue;
      }
      if (listed++ < input.maxFindings()) {
        lines.add(player.getValue() + " " + roles);
      }
    }
    if (listed > input.maxFindings()) {
      lines.add("and " + (listed - input.maxFindings()) + " more tracked players");
    }
    Set<TeamSide> sides = EnumSet.noneOf(TeamSide.class);
    input.namesBySlot().keySet().forEach(slot -> sides.add(TeamSide.ofSlot(slot)));
    List<TeamCompositionDomain> compositions = compositionRepo.findByMatchId(input.matchId());
    for (TeamSide side : TeamSide.values()) {
      if (!sides.contains(side)) {
        continue;
      }
      for (CompositionStage stage : CompositionStage.values()) {
        compositions.stream()
            .filter(c -> side.name().equals(c.getTeam()) && stage.name().equals(c.getStage()))
            .findFirst()
            .ifPresent(
                c ->
                    lines.add(
                        capped(
                            label(side) + " " + STAGE_LABELS.get(stage) + ": " + c.getSummary())));
      }
    }
    if (lines.isEmpty()) {
      return List.of();
    }
    lines.addFirst("**Roles and composition**");
    return lines;
  }

  private String capped(String line) {
    int max = Math.max(ELLIPSIS.length() + 1, properties.getMaxCompositionLineLength());
    return line.length() <= max ? line : line.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
  }

  private static String roles(List<PlayerRoleProfileDomain> profiles, long slot) {
    String intended = role(profiles, slot, RoleStage.INTENT);
    String played = role(profiles, slot, RoleStage.FINAL);
    if (intended == null && played == null) {
      return null;
    }
    if (intended == null) {
      return "played " + played;
    }
    if (played == null) {
      return "was meant to play " + intended;
    }
    return intended.equals(played)
        ? "was meant to play and played " + played
        : "was meant to play " + intended + " and played " + played;
  }

  private static String role(List<PlayerRoleProfileDomain> profiles, long slot, RoleStage stage) {
    return profiles.stream()
        .filter(p -> p.getPlayerSlot() == slot && stage.name().equals(p.getStage()))
        .map(PlayerRoleProfileDomain::getRole)
        .filter(ROLES::containsKey)
        .map(ROLES::get)
        .findFirst()
        .orElse(null);
  }

  private static String label(TeamSide side) {
    String name = side.name().toLowerCase(Locale.ROOT);
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }
}
