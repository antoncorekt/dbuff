package com.ako.dbuff.service.replay.defenseloop;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MissingDefenseItemDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.TeamDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MissingDefenseItemRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.TeamDefenseItemRepo;
import com.ako.dbuff.dao.repo.TeamDefensePointRepo;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.analysis.defense.DefenseItemBonuses;
import com.ako.dbuff.service.replay.analysis.defense.DefenseItemBonuses.Bonus;
import com.ako.dbuff.service.replay.analysis.defense.DefenseMath;
import com.ako.dbuff.service.replay.analysis.defense.DefenseProperties;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimelines;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff.Section;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopTopic;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.report.DefenseChartBuilder;
import com.ako.dbuff.service.replay.report.Prettify;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import com.ako.dbuff.service.replay.report.chart.ChartService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The team defense loop: both teams' health, estimated armor, resistance and effective health at
 * the checkpoints, the defensive items, the missing-item findings, and for every hero the pieces
 * armor and resistance are added up from, so a reviewer can check them against the game.
 */
@Component
class DefenseLoopTopic implements ReviewLoopTopic {
  static final Path MATCH_LIST = Path.of("src/test/resources/replay/defense-loop/matches.txt");
  static final String TEAMS = "Team defense";
  static final String ITEMS = "Defensive items";
  static final String MISSING = "Missing defensive items";
  static final String HEROES = "Hero armor and magic resistance";
  static final List<Section> SECTIONS =
      List.of(
          new Section(TEAMS, "", 2),
          new Section(ITEMS, "", 4),
          new Section(MISSING, "", 3),
          new Section(HEROES, "", 3));

  private static final Path OUTPUT_DIR = Path.of("build", "defense-loop");
  private static final String ROUND_PREFIX = "round-";
  private static final List<String> CHECKPOINTS = List.of("M10", "M20", "M30", "END");
  private static final int BREAKDOWN_MINUTE = 20;
  private static final int SECONDS_PER_MINUTE = 60;
  private static final String NONE = "-";

  private static final String HOW_TO_READ =
      "\"Team defense\" has, per team at 10:00, 20:00, 30:00 and the end: summed max HP, the"
          + " average estimated armor and magic resistance, the three effective HP sums"
          + " (physical, magical, mixed by the damage the enemy dealt in the last minutes), the"
          + " enemy's damage shares and net worth. \"Defensive items\" is what the analysis found,"
          + " \"Missing defensive items\" the teams that took mostly one damage type without an"
          + " item against it. \"Hero armor and magic resistance\" adds up each hero's value at"
          + " 20:00 and at the end: base armor + agility armor + item armor = total, base magic"
          + " resistance and the item resistances (each stacks multiplicatively) = total. Armor and"
          + " resistance are estimates: auras, talents and buffs are not counted. The graph is"
          + " saved next to this file.";

  private final StoredMatchModes matchModes;
  private final MatchRepo matchRepo;
  private final ReportHeroNames heroNames;
  private final TeamDefensePointRepo pointRepo;
  private final TeamDefenseItemRepo itemRepo;
  private final MissingDefenseItemRepo missingRepo;
  private final ReplayPlayerTrackRepo trackRepo;
  private final HeroStateTimelines timelines;
  private final DefenseItemBonuses bonuses;
  private final DefenseProperties properties;
  private final DefenseChartBuilder chartBuilder;
  private final ChartService charts;
  private Path roundDir;

  DefenseLoopTopic(
      StoredMatchModes matchModes,
      MatchRepo matchRepo,
      ReportHeroNames heroNames,
      TeamDefensePointRepo pointRepo,
      TeamDefenseItemRepo itemRepo,
      MissingDefenseItemRepo missingRepo,
      ReplayPlayerTrackRepo trackRepo,
      HeroStateTimelines timelines,
      DefenseItemBonuses bonuses,
      DefenseProperties properties,
      DefenseChartBuilder chartBuilder,
      ChartService charts) {
    this.matchModes = matchModes;
    this.matchRepo = matchRepo;
    this.heroNames = heroNames;
    this.pointRepo = pointRepo;
    this.itemRepo = itemRepo;
    this.missingRepo = missingRepo;
    this.trackRepo = trackRepo;
    this.timelines = timelines;
    this.bonuses = bonuses;
    this.properties = properties;
    this.chartBuilder = chartBuilder;
    this.charts = charts;
  }

  /** What one match report is made of. */
  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      Map<Long, String> heroNames,
      List<TeamDefensePointDomain> points,
      List<TeamDefenseItemDomain> items,
      List<MissingDefenseItemDomain> missing,
      List<ReplayPlayerTrackDomain> tracks,
      Map<Integer, HeroStateTimeline> timelines,
      DefenseItemBonuses bonuses,
      DefenseProperties properties,
      String graphFile) {}

  @Override
  public String name() {
    return "defense";
  }

  @Override
  public Path matchList() {
    return MATCH_LIST;
  }

  @Override
  public Path outputDir() {
    return OUTPUT_DIR;
  }

  @Override
  public List<Section> sections() {
    return SECTIONS;
  }

  @Override
  public String render(long matchId) {
    String mode =
        matchModes.activeFor(matchId).map(GameModeProfile::name).orElse("mode not active");
    return render(
        new MatchData(
            matchId,
            matchRepo.findById(matchId).orElseThrow(),
            mode,
            heroNames.bySlot(matchId),
            pointRepo.findByMatchId(matchId),
            itemRepo.findByMatchId(matchId),
            missingRepo.findByMatchId(matchId),
            trackRepo.findByMatchId(matchId),
            timelines.load(matchId),
            bonuses,
            properties,
            saveGraph(matchId)));
  }

  private String saveGraph(long matchId) {
    return chartBuilder
        .build(matchId)
        .flatMap(chart -> charts.png("defense-loop:" + matchId, chart::spec))
        .map(
            png -> {
              String file = "%d-graph-defense.png".formatted(matchId);
              try {
                Path dir = roundDir();
                Files.createDirectories(dir);
                Files.write(dir.resolve(file), png);
                return file;
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .orElse(null);
  }

  private synchronized Path roundDir() {
    if (roundDir == null) {
      roundDir = OUTPUT_DIR.resolve(ROUND_PREFIX + "%02d".formatted(roundNumber()));
    }
    return roundDir;
  }

  private static int roundNumber() {
    String requested = System.getenv("ROUND");
    if (requested != null && !requested.isBlank()) {
      return Integer.parseInt(requested.strip());
    }
    if (!Files.isDirectory(OUTPUT_DIR)) {
      return 1;
    }
    try (Stream<Path> dirs = Files.list(OUTPUT_DIR)) {
      return dirs.map(d -> d.getFileName().toString())
              .filter(n -> n.matches(ROUND_PREFIX + "\\d+"))
              .mapToInt(n -> Integer.parseInt(n.substring(ROUND_PREFIX.length())))
              .max()
              .orElse(0)
          + 1;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String render(MatchData d) {
    List<String> out = new ArrayList<>();
    out.add(ReviewLoopReport.matchHeader(d.matchId(), d.modeName(), d.match()));
    out.add("");
    out.addAll(ReviewLoopReport.introduction(HOW_TO_READ, summary(d)));
    out.add("");
    out.add("**Graph (graph-defense):** " + (d.graphFile() == null ? "not drawn" : d.graphFile()));
    out.add("");
    out.addAll(teamTable(d));
    out.add("");
    out.addAll(itemTable(d));
    out.add("");
    out.addAll(missingTable(d));
    out.add("");
    out.addAll(heroTable(d));
    return String.join("\n", out) + "\n";
  }

  private static String summary(MatchData d) {
    if (d.points().isEmpty()) {
      return "no defense points stored (the analysis did not run, or the match has no tracks).";
    }
    return "%d team rows, %d defensive items, %d missing-item findings, %d heroes."
        .formatted(
            d.points().stream().filter(p -> p.getCheckpoint() != null).count(),
            d.items().size(),
            d.missing().size(),
            d.tracks().size());
  }

  private static List<String> table(String heading, String columns, int count) {
    return new ArrayList<>(
        List.of("## " + heading, "", "| " + columns + " |", "|" + "---|".repeat(count)));
  }

  private static String row(Object... cells) {
    return "| "
        + java.util.Arrays.stream(cells)
            .map(c -> ReviewLoopReport.tableCell(c == null ? NONE : String.valueOf(c)))
            .collect(Collectors.joining(" | "))
        + " |";
  }

  private static List<String> teamTable(MatchData d) {
    List<String> out =
        table(
            TEAMS,
            "team | checkpoint | minute | max HP | armor | magic resistance | EHP physical | EHP"
                + " magical | EHP mixed | enemy physical | enemy magical | enemy pure | net worth",
            13);
    d.points().stream()
        .filter(p -> p.getCheckpoint() != null)
        .sorted(
            Comparator.comparing(TeamDefensePointDomain::getTeam)
                .thenComparingInt(p -> CHECKPOINTS.indexOf(p.getCheckpoint())))
        .forEach(
            p ->
                out.add(
                    row(
                        p.getTeam(),
                        p.getCheckpoint(),
                        p.getMinute(),
                        p.getMaxHpSum(),
                        tenths(p.getArmorAvgX10(), 10),
                        percent(p.getMagicResistAvgX10(), 1000),
                        p.getEhpPhys(),
                        p.getEhpMag(),
                        p.getEhpMix(),
                        share(p.getEnemyPhysShare()),
                        share(p.getEnemyMagShare()),
                        share(p.getEnemyPureShare()),
                        p.getNetWorth())));
    return out;
  }

  private static List<String> itemTable(MatchData d) {
    List<String> out = table(ITEMS, "team | hero | item | at | kind", 5);
    d.items().stream()
        .sorted(
            Comparator.comparing(
                    TeamDefenseItemDomain::getAtSecond,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TeamDefenseItemDomain::getPlayerSlot))
        .forEach(
            i ->
                out.add(
                    row(
                        i.getTeam(),
                        hero(d, i.getPlayerSlot()),
                        Prettify.ability("item_" + i.getItem()),
                        clock(i.getAtSecond()),
                        i.getKind())));
    return out;
  }

  private static List<String> missingTable(MatchData d) {
    List<String> out =
        table(
            MISSING,
            "team | checkpoint | kind | minute | enemy share | with item (wins/games) | without"
                + " (wins/games) | evidence",
            8);
    d.missing().stream()
        .sorted(
            Comparator.comparing(MissingDefenseItemDomain::getTeam)
                .thenComparing(MissingDefenseItemDomain::getCheckpoint)
                .thenComparing(MissingDefenseItemDomain::getKind))
        .forEach(
            m ->
                out.add(
                    row(
                        m.getTeam(),
                        m.getCheckpoint(),
                        m.getKind(),
                        m.getMinute(),
                        share(m.getEnemyShare()),
                        m.getWithWins() + "/" + m.getWithGames(),
                        m.getWithoutWins() + "/" + m.getWithoutGames(),
                        m.getEvidence())));
    return out;
  }

  private static List<String> heroTable(MatchData d) {
    List<String> out =
        table(
            HEROES,
            "team | hero | at | base armor | agility armor | item armor | items | armor | base MR"
                + " | item MR | items | MR",
            12);
    double lastSecond =
        d.tracks().stream().mapToDouble(DefenseLoopTopic::lastSecond).max().orElse(0);
    for (ReplayPlayerTrackDomain track :
        d.tracks().stream()
            .sorted(Comparator.comparing(ReplayPlayerTrackDomain::getPlayerSlot))
            .toList()) {
      double breakdownSecond = BREAKDOWN_MINUTE * (double) SECONDS_PER_MINUTE;
      if (breakdownSecond <= lastSecond) {
        out.add(heroRow(d, track, "M" + BREAKDOWN_MINUTE, breakdownSecond));
      }
      out.add(heroRow(d, track, "END", lastSecond));
    }
    return out;
  }

  private static double lastSecond(ReplayPlayerTrackDomain t) {
    return t.getStartSeconds() + (t.getSampleCount() - 1.0) * t.getStepSeconds();
  }

  private static String heroRow(
      MatchData d, ReplayPlayerTrackDomain t, String checkpoint, double second) {
    int index =
        (int)
            Math.max(
                0,
                Math.min(
                    t.getSampleCount() - 1,
                    Math.round((second - t.getStartSeconds()) / t.getStepSeconds())));
    double sampleSecond = t.getStartSeconds() + (double) index * t.getStepSeconds();
    String team = TeamSide.ofSlot(t.getPlayerSlot()).name();
    String hero = hero(d, t.getPlayerSlot());
    Integer baseArmor = at(t.getBaseArmorX10(), index);
    Integer baseMr = at(t.getBaseMagicResistX10(), index);
    Integer agility = at(t.getAgilityTotalX10(), index);
    if (baseArmor == null || baseMr == null || agility == null) {
      return row(team, hero, checkpoint, "no stored values");
    }
    HeroStateTimeline timeline = d.timelines().get(PlayerSlots.index(t.getPlayerSlot()));
    List<String> held = timeline == null ? List.of() : List.copyOf(timeline.itemsAt(sampleSecond));
    List<Bonus> parts = held.stream().map(d.bonuses()::of).toList();
    double agilityArmor = agility / 10.0 * d.properties().getArmorPerAgility();
    double itemArmor = parts.stream().mapToDouble(Bonus::armor).sum();
    double armor =
        DefenseMath.armor(
            baseArmor / 10.0, agility / 10.0, d.properties().getArmorPerAgility(), parts);
    double mr = DefenseMath.magicResist(baseMr / 1000.0, parts);
    return row(
        team,
        hero,
        checkpoint,
        one(baseArmor / 10.0),
        one(agilityArmor),
        one(itemArmor),
        names(held, parts, Bonus::armor, v -> signed(v)),
        one(armor),
        percent(baseMr, 1000),
        one(100 * (1 - (1 - mr) / (1 - baseMr / 1000.0))) + "%",
        names(held, parts, Bonus::magicResist, v -> one(100 * v) + "%"),
        one(100 * mr) + "%");
  }

  private static String names(
      List<String> held,
      List<Bonus> parts,
      java.util.function.ToDoubleFunction<Bonus> value,
      java.util.function.DoubleFunction<String> format) {
    String text =
        IntStream.range(0, held.size())
            .filter(i -> value.applyAsDouble(parts.get(i)) != 0)
            .mapToObj(
                i ->
                    Prettify.ability(held.get(i))
                        + " "
                        + format.apply(value.applyAsDouble(parts.get(i))))
            .collect(Collectors.joining(", "));
    return text.isEmpty() ? NONE : text;
  }

  private static Integer at(int[] values, int index) {
    return values == null || index >= values.length ? null : values[index];
  }

  private static String hero(MatchData d, Long slot) {
    return slot == null ? NONE : d.heroNames().getOrDefault(slot, "slot " + slot);
  }

  private static String one(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }

  private static String signed(double value) {
    return (value >= 0 ? "+" : "") + one(value);
  }

  private static String tenths(Integer value, int scale) {
    return value == null ? NONE : one(value / (double) scale);
  }

  private static String percent(Integer value, int scale) {
    return value == null ? NONE : one(100.0 * value / scale) + "%";
  }

  private static String share(Double value) {
    return value == null ? NONE : one(100 * value) + "%";
  }

  private static String clock(Integer seconds) {
    return seconds == null ? NONE : "%d:%02d".formatted(seconds / 60, seconds % 60);
  }
}
