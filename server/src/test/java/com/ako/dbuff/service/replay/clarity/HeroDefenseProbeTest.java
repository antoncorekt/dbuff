package com.ako.dbuff.service.replay.clarity;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import skadistats.clarity.event.Insert;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.model.StringTable;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.OnEntityUpdated;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.processor.stringtables.StringTables;
import skadistats.clarity.processor.stringtables.UsesStringTable;
import skadistats.clarity.source.MappedFileSource;
import skadistats.clarity.wire.dota.common.proto.DOTACombatLog.DOTA_COMBATLOG_TYPES;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Probe, not a regression test: writes which hero properties hold total armor and magic resistance
 * and which combat-log fields carry the damage type, to {@code build/probe/hero-defense.md}.
 */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
public class HeroDefenseProbeTest {

  private static final Map<String, String> MATCHES = matches();
  private static final int[] SAMPLE_SECONDS = {0, 600, 1200};
  private static final int DAMAGE_ENTRIES = 200;
  private static final int GRID_TICKS = 150;
  private static final int TICKS_PER_SECOND = 30;
  private static final int GAME_IN_PROGRESS = 5;
  private static final int POST_GAME = 6;
  private static final String[] PROPERTY_WORDS = {
    "armor", "resist", "magical", "physical", "strength", "agility", "intellect"
  };
  private static final String[] FIELD_WORDS = {"damage_type", "damage_category", "type"};

  private static Map<String, String> matches() {
    Map<String, String> matches = new TreeMap<>();
    matches.put("9044278208", "All Pick");
    matches.put("9037038717", "Ability Draft");
    return matches;
  }

  @Test
  void writesHeroDefensePropertiesAndDamageTypeFields() throws IOException {
    StringBuilder out = new StringBuilder("# Hero defense probe\n");
    for (Map.Entry<String, String> match : MATCHES.entrySet()) {
      Collector collector = new Collector();
      new SimpleRunner(
              new MappedFileSource(
                  Path.of(System.getenv("REPLAY_FIXTURE_DIR"), match.getKey() + ".dem").toString()))
          .runWith(collector);
      out.append("\n## Match ")
          .append(match.getKey())
          .append(" (")
          .append(match.getValue())
          .append(")\n\n### Hero properties and items\n\n")
          .append("| time | hero | level | kind | name | value |\n|---|---|---|---|---|---|\n");
      collector.rows.forEach(row -> out.append(row).append('\n'));
      out.append("\n### First ")
          .append(DAMAGE_ENTRIES)
          .append(" damage entries\n\n")
          .append(
              "| time | attacker | target | inflictor | field | value |\n|---|---|---|---|---|---|\n");
      collector.damage.forEach(row -> out.append(row).append('\n'));
    }
    Path file = Path.of("build", "probe", "hero-defense.md");
    Files.createDirectories(file.getParent());
    Files.writeString(file, out.toString());

    assertThat(file).exists().isNotEmptyFile();
  }

  @UsesEntities
  @UsesStringTable("EntityNames")
  public static class Collector {
    @Insert Entities entities;
    @Insert StringTables stringTables;

    final List<String> rows = new ArrayList<>();
    final List<String> damage = new ArrayList<>();
    private final GameClock clock = new GameClock();
    private long startTick = -1;
    private long nextGrid;
    private boolean stopped;
    private int damageSeen;
    private int nextTarget;
    private List<String> last = List.of();

    @OnTickStart
    public void onTick(Context ctx, boolean synthetic) {
      if (startTick < 0 || stopped) {
        return;
      }
      long tick = clock.gameTick(ctx.getTick());
      if (tick < nextGrid) {
        return;
      }
      nextGrid = tick + GRID_TICKS;
      int seconds = (int) ((tick - startTick) / TICKS_PER_SECOND);
      List<String> sample = sample(seconds);
      if (nextTarget < SAMPLE_SECONDS.length && seconds >= SAMPLE_SECONDS[nextTarget]) {
        rows.addAll(sample);
        nextTarget++;
      }
      last = sample;
    }

    @OnEntityUpdated(classPattern = "CDOTAGamerulesProxy")
    public void rulesUpdated(Context ctx, Entity rules, FieldPath[] paths, int count) {
      String stateKey = "m_pGameRules.m_nGameState";
      String pausedKey = "m_pGameRules.m_bGamePaused";
      if (rules.hasProperty(pausedKey) && rules.getProperty(pausedKey) instanceof Boolean paused) {
        clock.paused(paused, ctx.getTick());
      }
      if (!rules.hasProperty(stateKey)) {
        return;
      }
      int state = rules.getProperty(stateKey);
      if (state == GAME_IN_PROGRESS && startTick < 0) {
        startTick = clock.gameTick(ctx.getTick());
        nextGrid = startTick;
      } else if (state == POST_GAME && startTick >= 0 && !stopped) {
        stopped = true;
        rows.addAll(last);
      }
    }

    @OnCombatLogEntry
    public void onCombatLog(CombatLogEntry entry) {
      if (damageSeen >= DAMAGE_ENTRIES
          || entry.getType() != DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_DAMAGE
          || !(entry.isAttackerHero() || entry.isTargetHero())) {
        return;
      }
      damageSeen++;
      String head =
          "| %.1f | %s | %s | %s | "
              .formatted(
                  entry.getTimestamp(),
                  entry.getAttackerName(),
                  entry.getTargetName(),
                  entry.getInflictorName());
      for (Method method : CombatLogEntry.class.getMethods()) {
        String name = method.getName();
        if (method.getParameterCount() != 0
            || !(name.startsWith("get") || name.startsWith("is"))
            || !contains(name, FIELD_WORDS)) {
          continue;
        }
        try {
          damage.add(head + name + " | " + method.invoke(entry) + " |");
        } catch (ReflectiveOperationException e) {
          damage.add(head + name + " | unreadable: " + e.getMessage() + " |");
        }
      }
    }

    private List<String> sample(int seconds) {
      List<String> out = new ArrayList<>();
      String time = "%d:%02d".formatted(seconds / 60, seconds % 60);
      StringTable names = stringTables.forName("EntityNames");
      entities.stream()
          .filter(Entities.byDtName("CDOTAPlayerController"))
          .forEach(
              controller -> {
                Object handle =
                    controller.hasProperty("m_hAssignedHero")
                        ? controller.getProperty("m_hAssignedHero")
                        : null;
                Entity hero =
                    handle instanceof Number n && n.intValue() > 0
                        ? entities.getByHandle(n.intValue())
                        : null;
                if (hero == null) {
                  return;
                }
                String prefix =
                    "| %s | %s | %s | ".formatted(time, hero.getDtClass().getDtName(), level(hero));
                Iterator<FieldPath> paths = hero.getState().fieldPathIterator();
                while (paths.hasNext()) {
                  FieldPath path = paths.next();
                  String name = hero.getNameForFieldPath(path);
                  if (name == null) {
                    continue;
                  }
                  if (contains(name, PROPERTY_WORDS)) {
                    out.add(
                        prefix
                            + "property | "
                            + name
                            + " | "
                            + hero.getPropertyForFieldPath(path)
                            + " |");
                  } else if (name.startsWith("m_hItems.")) {
                    Object value = hero.getPropertyForFieldPath(path);
                    Entity item =
                        value instanceof Number n && n.intValue() > 0
                            ? entities.getByHandle(n.intValue())
                            : null;
                    out.add(prefix + "item | " + name + " | " + itemName(names, item) + " |");
                  }
                }
              });
      return out;
    }

    private static Object level(Entity hero) {
      return hero.hasProperty("m_iCurrentLevel") ? hero.getProperty("m_iCurrentLevel") : "?";
    }

    private static String itemName(StringTable names, Entity item) {
      if (item == null) {
        return "";
      }
      Object index =
          item.hasProperty("m_pEntity.m_nameStringTableIndex")
              ? item.getProperty("m_pEntity.m_nameStringTableIndex")
              : null;
      return index instanceof Number n && n.intValue() >= 0
          ? names.getNameByIndex(n.intValue())
          : "";
    }

    private static boolean contains(String name, String[] words) {
      String lower = name.toLowerCase(Locale.ROOT);
      for (String word : words) {
        if (lower.contains(word)) {
          return true;
        }
      }
      return false;
    }
  }
}
