package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.report.SectionViews.Verdict;
import com.ako.dbuff.service.replay.report.chart.ChartPalette;
import com.ako.dbuff.service.replay.report.chart.SkillIconImages;
import com.ako.dbuff.service.replay.report.chart.TableRenderer;
import com.ako.dbuff.service.replay.report.chart.TableSpec;
import com.ako.dbuff.service.replay.report.chart.TableSpec.Cell;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** What each tracked player drafted in an Ability Draft and in which overall pick. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DraftSection implements ReplayReportSection {
  private static final int DELIVERIES_KEPT = 8;
  private static final String AD_SUFFIX = "_ad";
  static final String NOT_DRAWN = "The picture could not be drawn this time.";

  private final AbilityDraftPickRepo pickRepo;
  private final PlayerGameStatisticRepo statRepo;
  private final AbilityRepo abilityRepo;
  private final AbilityDraftProfile profile;
  private final AbilityLabels labels;
  private final HeroKits heroKits;
  private final SkillIconImages icons;
  private final TableRenderer tables;
  private final PlainViewRenderer plain = new PlainViewRenderer();
  private final Cache<ReplayReportInput, Optional<SectionView>> views =
      Caffeine.newBuilder().weakKeys().maximumSize(DELIVERIES_KEPT).build();

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == profile.gameModeId();
  }

  @Override
  public String name() {
    return "draft";
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public int order() {
    return 10;
  }

  @Override
  public String summary() {
    return "the draft, with each hero's skills";
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return view(input).map(plain::lines).orElse(List.of());
  }

  @Override
  public Optional<ImageAttachment> image(ReplayReportInput input) {
    return view(input).map(SectionView::image);
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    return views.get(input, this::build);
  }

  private Optional<SectionView> build(ReplayReportInput input) {
    List<Drafter> drafters = drafters(input);
    if (drafters.isEmpty()) {
      return Optional.empty();
    }
    List<String> bullets = new ArrayList<>();
    Optional<ImageAttachment> image = picture(input.matchId(), drafters);
    if (image.isEmpty()) {
      for (Drafter drafter : drafters) {
        bullets.add(drafter.name());
        drafter
            .picks()
            .forEach(
                p ->
                    bullets.add("- " + labels.decorated(p.getAbilityName()) + " · " + position(p)));
      }
      bullets.add(NOT_DRAWN);
    }
    Verdict verdict =
        new Verdict(
            "Ability picks of " + SectionViews.count(drafters.size(), "tracked player"),
            Accent.NEUTRAL);
    return Optional.of(
        new SectionView(
            "Draft and stats",
            verdict.text(),
            verdict.accent(),
            bullets,
            null,
            image.orElse(null)));
  }

  private record Drafter(
      long slot,
      String name,
      Long heroId,
      PlayerMatchStatisticDomain stat,
      List<AbilityDraftPickDomain> picks) {}

  private List<Drafter> drafters(ReplayReportInput input) {
    List<AbilityDraftPickDomain> picks = pickRepo.findByMatchId(input.matchId());
    Map<Long, PlayerMatchStatisticDomain> statBySlot = new HashMap<>();
    statRepo
        .findAllByMatchId(input.matchId())
        .forEach(
            h -> {
              if (h.getPlayerSlot() != null) {
                statBySlot.put(h.getPlayerSlot(), h);
              }
            });
    List<Drafter> drafters = new ArrayList<>();
    for (Map.Entry<Long, String> player : input.namesBySlot().entrySet()) {
      List<AbilityDraftPickDomain> chosen =
          picks.stream()
              .filter(p -> player.getKey().equals(p.getPlayerSlot()) && p.getAbilityName() != null)
              .sorted(
                  Comparator.comparing(
                      AbilityDraftPickDomain::getPickOrder,
                      Comparator.nullsLast(Comparator.naturalOrder())))
              .toList();
      if (!chosen.isEmpty()) {
        PlayerMatchStatisticDomain stat = statBySlot.get(player.getKey());
        drafters.add(
            new Drafter(
                player.getKey(),
                player.getValue(),
                stat == null ? null : stat.getHeroId(),
                stat,
                chosen));
      }
    }
    return drafters;
  }

  private Optional<ImageAttachment> picture(long matchId, List<Drafter> drafters) {
    try {
      int skills = drafters.stream().mapToInt(d -> d.picks().size()).max().orElse(0);
      Map<Long, List<AbilityDomain>> damageBySlot = new HashMap<>();
      abilityRepo
          .findAllByMatchId(matchId)
          .forEach(
              a -> damageBySlot.computeIfAbsent(a.getPlayerSlot(), k -> new ArrayList<>()).add(a));
      List<String> headers = new ArrayList<>(List.of("Player", "K/D/A, GPM/XPM"));
      for (int i = 1; i <= skills; i++) {
        headers.add("Skill " + i);
      }
      List<List<Cell>> rows = new ArrayList<>();
      for (Drafter drafter : drafters) {
        Color band =
            TeamSide.ofSlot(drafter.slot()) == TeamSide.RADIANT
                ? ChartPalette.RADIANT
                : ChartPalette.DIRE;
        Optional<HeroConstant> hero =
            drafter.heroId() == null ? Optional.empty() : heroKits.hero(drafter.heroId());
        byte[] heroIcon =
            drafter.heroId() == null ? null : icons.hero(drafter.heroId()).orElse(null);
        String player =
            hero.map(h -> drafter.name() + " (" + h.getLocalized_name() + ")")
                .orElse(drafter.name());
        List<Cell> row = new ArrayList<>();
        row.add(new Cell(heroIcon, player, null, band));
        row.add(kda(drafter.stat()));
        List<AbilityDomain> damage = damageBySlot.getOrDefault(drafter.slot(), List.of());
        for (AbilityDraftPickDomain pick : drafter.picks()) {
          row.add(
              new Cell(
                  icons.ability(pick.getAbilityName()).orElse(null),
                  labels.label(pick.getAbilityName()),
                  null,
                  null,
                  sub(pick, damage)));
        }
        rows.add(row);
      }
      return tables
          .render(new TableSpec("Draft and stats", headers, rows))
          .map(png -> new ImageAttachment("draft-" + matchId + ".png", png));
    } catch (RuntimeException e) {
      log.warn("Draft picture of match {} failed: {}", matchId, e.toString(), e);
      return Optional.empty();
    }
  }

  private static Cell kda(PlayerMatchStatisticDomain stat) {
    String score =
        stat == null
            ? "?/?/?"
            : BehaviourWording.gold(stat.getKills())
                + "/"
                + BehaviourWording.gold(stat.getDeaths())
                + "/"
                + BehaviourWording.gold(stat.getAssists());
    String rates =
        stat == null
            ? "?/?"
            : BehaviourWording.gold(stat.getGoldPerMin())
                + "/"
                + BehaviourWording.gold(stat.getXpPerMin());
    return new Cell(null, score, null, null, rates);
  }

  private static String sub(AbilityDraftPickDomain pick, List<AbilityDomain> damage) {
    List<String> parts = new ArrayList<>();
    Integer number = number(pick);
    if (number != null) {
      parts.add("#" + number);
    } else if (pick.getTeamPickIndex() != null) {
      parts.add("team pick " + pick.getTeamPickIndex());
    }
    damageRow(pick.getAbilityName(), damage)
        .ifPresent(
            row -> {
              parts.add(BehaviourWording.gold(row.getDamageDealt()));
              parts.add(row.getUseCount() == null ? "?x" : row.getUseCount() + "x");
            });
    return parts.isEmpty() ? null : String.join(" · ", parts);
  }

  private static Optional<AbilityDomain> damageRow(String ability, List<AbilityDomain> damage) {
    Optional<AbilityDomain> own =
        damage.stream().filter(a -> ability.equals(a.getName())).findFirst();
    if (own.isPresent() || !ability.endsWith(AD_SUFFIX)) {
      return own;
    }
    String base = ability.substring(0, ability.length() - AD_SUFFIX.length());
    return damage.stream().filter(a -> base.equals(a.getName())).findFirst();
  }

  private static String position(AbilityDraftPickDomain pick) {
    Integer number = number(pick);
    if (number != null) {
      return "pick " + number;
    }
    return pick.getTeamPickIndex() == null ? "" : "team pick " + pick.getTeamPickIndex();
  }

  private static Integer number(AbilityDraftPickDomain pick) {
    return pick.getTurnNumber() != null ? pick.getTurnNumber() : pick.getPickOrder();
  }
}
