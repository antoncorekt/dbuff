package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.dao.model.HeroDraftPickDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.HeroDraftPickRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.report.chart.ChartPalette;
import com.ako.dbuff.service.replay.report.chart.SkillIconImages;
import com.ako.dbuff.service.replay.report.chart.TableRenderer;
import com.ako.dbuff.service.replay.report.chart.TableSpec;
import com.ako.dbuff.service.replay.report.chart.TableSpec.Cell;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
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

/**
 * The Ability Draft as one board: every turn, skills and heroes together, in the Radiant or the
 * Dire column of a picture, with who picked; what nobody took is the last block. The bullets list
 * the same turns one per line.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbilityDraftBoard implements DraftBoard {
  static final String NOT_DRAWN = "The picture could not be drawn this time.";

  private static final String FOOTNOTE =
      "Every turn of the draft, skills and heroes counted together, with the player who took it.";
  private static final String ORDER_UNKNOWN =
      " The turns are not known for this match; the picks are in the order they were stored.";
  private static final int PICTURE_ITEMS_PER_ROW = 2;
  private static final int DELIVERIES_KEPT = 8;

  private final HeroDraftPickRepo heroRepo;
  private final AbilityDraftPickRepo skillRepo;
  private final AbilityDraftPoolRepo poolRepo;
  private final PlayerGameStatisticRepo statRepo;
  private final AbilityDraftProfile profile;
  private final AbilityLabels labels;
  private final ReportHeroNames heroNames;
  private final SkillIconImages icons;
  private final TableRenderer tables;
  private final Cache<ReplayReportInput, Optional<SectionView>> views =
      Caffeine.newBuilder().weakKeys().maximumSize(DELIVERIES_KEPT).build();

  @Override
  public long gameModeId() {
    return profile.gameModeId();
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    return views.get(input, this::build);
  }

  private Optional<SectionView> build(ReplayReportInput input) {
    List<HeroDraftPickDomain> heroes = heroRepo.findByMatchId(input.matchId());
    if (heroes.isEmpty()) {
      return Optional.empty();
    }
    List<AbilityDraftPickDomain> skills = skillRepo.findByMatchId(input.matchId());
    List<AbilityDraftPoolDomain> pool = poolRepo.findByMatchId(input.matchId());
    Board board = new Board(input, heroes);
    List<Turn> turns = board.turns(skills);
    List<Leftover> leftOver = board.leftOver(pool);
    List<String> bullets = new ArrayList<>();
    bullets.addAll(
        BoardRows.bullets("**Picks in draft order**", turns.stream().map(Turn::line).toList()));
    bullets.addAll(BoardRows.bullets("**Left over**", board.leftOverLines(leftOver)));
    Optional<ImageAttachment> image = picture(input, turns, leftOver);
    if (image.isEmpty()) {
      bullets.add(NOT_DRAWN);
    }
    boolean numbersMissing =
        heroes.stream().anyMatch(h -> h.getPlayerSlot() != null && h.getTurnNumber() == null)
            || skills.stream()
                .anyMatch(s -> s.getPlayerSlot() != null && s.getTurnNumber() == null);
    return Optional.of(
        new SectionView(
            "Draft board",
            null,
            Accent.INFO,
            bullets,
            numbersMissing ? FOOTNOTE + ORDER_UNKNOWN : FOOTNOTE,
            image.orElse(null)));
  }

  /** One turn of the draft: what was picked, by whom, shown as text and as picture cells. */
  private record Turn(
      Integer number,
      TeamSide side,
      String line,
      String abilityName,
      Long heroId,
      String name,
      String who,
      boolean tracked) {}

  private record Leftover(String abilityName, Long heroId, String name, String line) {}

  /** One pick of a player, a skill or the hero, with what orders it in the draft. */
  private record Pick(
      long slot,
      Integer turn,
      Integer tick,
      boolean hero,
      int order,
      String abilityName,
      Long abilityId,
      Long heroId) {
    /** Turn numbers first; picks without one follow by tick, a skill before a hero on a tie. */
    static final Comparator<Pick> DRAFT_ORDER =
        Comparator.comparing((Pick p) -> p.turn == null)
            .thenComparing(Pick::turn, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Pick::tick, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Pick::hero)
            .thenComparingInt(Pick::order);
  }

  private Optional<ImageAttachment> picture(
      ReplayReportInput input, List<Turn> turns, List<Leftover> leftOver) {
    try {
      List<Turn> radiant = turns.stream().filter(t -> t.side() == TeamSide.RADIANT).toList();
      List<Turn> dire = turns.stream().filter(t -> t.side() == TeamSide.DIRE).toList();
      List<List<Cell>> rows = new ArrayList<>();
      for (int i = 0; i < Math.max(radiant.size(), dire.size()); i++) {
        List<Cell> row = new ArrayList<>(turnCells(i < radiant.size() ? radiant.get(i) : null));
        row.addAll(turnCells(i < dire.size() ? dire.get(i) : null));
        rows.add(row);
      }
      addLeftOver(rows, leftOver);
      return tables
          .render(
              new TableSpec(
                  "Draft board", List.of("Radiant", "Picked by", "Dire", "Picked by"), rows))
          .map(png -> new ImageAttachment("draft-board-" + input.matchId() + ".png", png));
    } catch (RuntimeException e) {
      log.warn("Draft board picture of match {} failed: {}", input.matchId(), e.toString(), e);
      return Optional.empty();
    }
  }

  private List<Cell> turnCells(Turn turn) {
    if (turn == null) {
      return List.of(Cell.text(""), Cell.text(""));
    }
    byte[] icon = icon(turn.heroId(), turn.abilityName());
    Color band =
        !turn.tracked()
            ? null
            : turn.side() == TeamSide.RADIANT ? ChartPalette.RADIANT : ChartPalette.DIRE;
    return List.of(
        new Cell(icon, turn.name(), turn.number() == null ? null : "#" + turn.number(), null),
        new Cell(null, turn.who(), null, band));
  }

  private byte[] icon(Long heroId, String abilityName) {
    if (heroId != null) {
      return icons.hero(heroId).orElse(null);
    }
    return abilityName == null ? null : icons.ability(abilityName).orElse(null);
  }

  private void addLeftOver(List<List<Cell>> rows, List<Leftover> leftOver) {
    if (leftOver.isEmpty()) {
      return;
    }
    int room = TableRenderer.MAX_ROWS - rows.size() - 1;
    if (room < 1) {
      return;
    }
    rows.add(List.of(new Cell(null, "Left over", null, null)));
    int capacity = room * PICTURE_ITEMS_PER_ROW;
    List<Leftover> shown = leftOver;
    int hidden = 0;
    if (leftOver.size() > capacity) {
      hidden = leftOver.size() - capacity + 1;
      shown = leftOver.subList(0, capacity - 1);
    }
    List<Cell> items = new ArrayList<>();
    for (Leftover item : shown) {
      byte[] icon = icon(item.heroId(), item.abilityName());
      items.add(new Cell(icon, item.name(), null, null));
    }
    if (hidden > 0) {
      items.add(Cell.text("+" + hidden + " more"));
    }
    for (int i = 0; i < items.size(); i += PICTURE_ITEMS_PER_ROW) {
      Cell second = i + 1 < items.size() ? items.get(i + 1) : Cell.text("");
      rows.add(List.of(items.get(i), Cell.text(""), second));
    }
  }

  private final class Board {
    private final ReplayReportInput input;
    private final List<HeroDraftPickDomain> heroes;
    private final Map<Long, Long> heroIdBySlot = new HashMap<>();

    Board(ReplayReportInput input, List<HeroDraftPickDomain> heroes) {
      this.input = input;
      this.heroes = heroes;
      heroes.stream()
          .filter(h -> h.getPlayerSlot() != null)
          .forEach(h -> heroIdBySlot.put(h.getPlayerSlot(), h.getHeroId()));
      statRepo
          .findAllByMatchId(input.matchId())
          .forEach(
              h -> {
                if (h.getPlayerSlot() != null && h.getHeroId() != null) {
                  heroIdBySlot.put(h.getPlayerSlot(), h.getHeroId());
                }
              });
    }

    List<Turn> turns(List<AbilityDraftPickDomain> skills) {
      List<Pick> picks = new ArrayList<>();
      skills.stream()
          .filter(s -> s.getPlayerSlot() != null)
          .forEach(
              s ->
                  picks.add(
                      new Pick(
                          s.getPlayerSlot(),
                          s.getTurnNumber(),
                          s.getPickTick(),
                          false,
                          s.getPickOrder() == null ? 0 : s.getPickOrder(),
                          s.getAbilityName(),
                          s.getAbilityId(),
                          null)));
      heroes.stream()
          .filter(h -> h.getPlayerSlot() != null)
          .forEach(
              h ->
                  picks.add(
                      new Pick(
                          h.getPlayerSlot(),
                          h.getTurnNumber(),
                          h.getPickTick(),
                          true,
                          0,
                          null,
                          null,
                          h.getHeroId())));
      picks.sort(Pick.DRAFT_ORDER);
      boolean numbered = picks.stream().anyMatch(p -> p.turn() != null);
      List<Turn> turns = new ArrayList<>();
      for (Pick pick : picks) {
        turns.add(turn(numbered ? pick.turn() : Integer.valueOf(turns.size() + 1), pick));
      }
      return turns;
    }

    private Turn turn(Integer number, Pick pick) {
      TeamSide side = TeamSide.ofSlot(pick.slot());
      String plainName =
          pick.hero()
              ? "hero " + heroNames.heroName(pick.heroId())
              : pick.abilityName() == null
                  ? "Skill " + pick.abilityId()
                  : labels.label(pick.abilityName());
      String decorated =
          pick.hero()
              ? plainName
              : pick.abilityName() == null ? plainName : labels.decorated(pick.abilityName());
      String who = who(pick.slot());
      String line =
          (number == null ? "" : number + ". ")
              + Prettify.team(side.name())
              + " · "
              + who
              + ": "
              + decorated;
      return new Turn(
          number,
          side,
          line,
          pick.abilityName(),
          pick.heroId(),
          plainName,
          who,
          input.namesBySlot().containsKey(pick.slot()));
    }

    private String who(long slot) {
      String picker = input.namesBySlot().get(slot);
      Long heroId = heroIdBySlot.get(slot);
      String hero = heroId == null ? null : heroNames.heroName(heroId);
      if (picker != null) {
        return picker + (hero == null ? "" : " (" + hero + ")");
      }
      if (hero != null) {
        return hero;
      }
      long index = slot >= 128 ? slot - 127 : slot + 1;
      return Prettify.team(TeamSide.ofSlot(slot).name()) + " player " + index;
    }

    List<Leftover> leftOver(List<AbilityDraftPoolDomain> pool) {
      List<Leftover> left = new ArrayList<>();
      heroes.stream()
          .filter(h -> h.getPlayerSlot() == null)
          .forEach(
              h -> {
                String name = heroNames.heroName(h.getHeroId());
                left.add(new Leftover(null, h.getHeroId(), name, name));
              });
      pool.stream()
          .filter(p -> p.getPickedBySlot() == null)
          .sorted(Comparator.comparing(AbilityDraftPoolDomain::getPoolIndex))
          .forEach(
              p ->
                  left.add(
                      p.getAbilityName() == null
                          ? new Leftover(
                              null, null, "Skill " + p.getAbilityId(), "Skill " + p.getAbilityId())
                          : new Leftover(
                              p.getAbilityName(),
                              null,
                              labels.label(p.getAbilityName()),
                              labels.decorated(p.getAbilityName()))));
      return left;
    }

    List<String> leftOverLines(List<Leftover> left) {
      List<String> rows = new ArrayList<>();
      List<String> leftHeroes =
          left.stream().filter(l -> l.heroId() != null).map(Leftover::line).toList();
      List<String> leftSkills =
          left.stream().filter(l -> l.heroId() == null).map(Leftover::line).toList();
      if (!leftHeroes.isEmpty()) {
        rows.addAll(wrapped("Heroes: ", leftHeroes));
      }
      if (!leftSkills.isEmpty()) {
        rows.addAll(wrapped("Skills: ", leftSkills));
      }
      return rows;
    }
  }

  /** A wrapped line stays well under the 2000 characters of a plain message. */
  private static final int ROW_LIMIT = 1500;

  private static List<String> wrapped(String lead, List<String> parts) {
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder(lead);
    boolean empty = true;
    for (String part : parts) {
      if (!empty && line.length() + 2 + part.length() > ROW_LIMIT) {
        lines.add(line.append(',').toString());
        line = new StringBuilder(part);
      } else {
        line.append(empty ? "" : ", ").append(part);
      }
      empty = false;
    }
    lines.add(line.toString());
    return lines;
  }
}
