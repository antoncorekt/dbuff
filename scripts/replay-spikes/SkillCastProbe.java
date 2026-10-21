package spike;

import java.util.*;
import java.util.regex.Pattern;
import skadistats.clarity.event.Insert;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.OnEntityCreated;
import skadistats.clarity.processor.entities.OnEntityDeleted;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;

@UsesEntities
public class SkillCastProbe {
  static final Set<String> SKILLS =
      Set.of("pudge_meat_hook", "tusk_ice_shards", "oracle_false_promise");
  static final Pattern PROPS =
      Pattern.compile("Visib|Tagged|Rotation|angRot|m_ang|CBodyComponent", Pattern.CASE_INSENSITIVE);
  static final Pattern WIDER =
      Pattern.compile("Vision|Visible|Seen|Reveal|Hidden|Detect|Invis|Team|Facing|Yaw", Pattern.CASE_INSENSITIVE);
  static final Pattern UNIT_PROPS =
      Pattern.compile("Unit|Owner|Name|Die|Life|Expire|Parent", Pattern.CASE_INSENSITIVE);
  static final int TICKS_PER_SECOND = 30;

  record Cast(int tick, float time, String attacker, String skill) {}

  @Insert Entities entities;

  final List<Cast> casts = new ArrayList<>();
  final Map<String, Integer> lastSeen = new HashMap<>();
  final Map<String, Object> lastCell = new HashMap<>();
  final TreeMap<Integer, Integer> gaps = new TreeMap<>();
  final Map<String, Integer> updates = new TreeMap<>();
  boolean dumped;
  final Map<Integer, Integer> watched = new HashMap<>();

  Cast activeCast(float time, int windowSeconds) {
    for (int i = casts.size() - 1; i >= 0; i--) {
      Cast c = casts.get(i);
      if (time - c.time() > windowSeconds) return null;
      if (time >= c.time()) return c;
    }
    return null;
  }

  @OnCombatLogEntry
  public void onCle(Context ctx, CombatLogEntry c) {
    String type = c.getType().name();
    String infl = String.valueOf(c.getInflictorName());
    float t = c.getTimestamp();
    int tick = ctx.getTick();
    switch (type) {
      case "DOTA_COMBATLOG_ABILITY" -> {
        if (!SKILLS.contains(infl)) return;
        casts.add(new Cast(tick, t, String.valueOf(c.getAttackerName()), infl));
        System.out.printf(
            "(a) tick=%d t=%.1f attacker=%s target=%s ability=%s loc=(%.0f,%.0f) value=%d%n",
            tick, t, c.getAttackerName(), c.getTargetName(), infl,
            c.getLocationX(), c.getLocationY(), c.getValue());
        if (!dumped) {
          dumped = true;
          dumpHero(c.getAttackerName());
        }
      }
      case "DOTA_COMBATLOG_MODIFIER_ADD", "DOTA_COMBATLOG_MODIFIER_REMOVE" -> {
        Cast cast = activeCast(t, infl.contains("false_promise") ? 12 : 3);
        if (cast != null)
          System.out.printf(
              "(b) %s tick=%d dt=%.1f cast=%s modifier=%s attacker=%s target=%s duration=%.1f loc=(%.0f,%.0f)%n",
              type.substring(type.lastIndexOf('_') - 3), tick, t - cast.time(), cast.skill(),
              infl, c.getAttackerName(), c.getTargetName(), c.getModifierDuration(),
              c.getLocationX(), c.getLocationY());
      }
      case "DOTA_COMBATLOG_DAMAGE", "DOTA_COMBATLOG_HEAL" -> {
        boolean match = SKILLS.stream().anyMatch(s -> infl.contains(s.substring(s.indexOf('_') + 1)));
        Cast cast = activeCast(t, 12);
        if (match || (cast != null && infl.contains("false_promise")))
          System.out.printf(
              "(f) %s tick=%d t=%.1f inflictor=%s attacker=%s target=%s value=%d health=%d%n",
              type.substring(type.lastIndexOf('_') + 1), tick, t, infl,
              c.getAttackerName(), c.getTargetName(), c.getValue(), c.getHealth());
      }
      default -> {}
    }
  }

  @OnEntityCreated
  public void created(Context ctx, Entity e) {
    if (casts.isEmpty()) return;
    int tick = ctx.getTick();
    for (int i = casts.size() - 1; i >= 0; i--) {
      Cast c = casts.get(i);
      if (tick - c.tick() > 2 * TICKS_PER_SECOND) break;
      String cls = e.getDtClass().getDtName();
      if (cls.startsWith("CDOTA_Unit_Hero_")) return;
      watched.put(e.getIndex(), tick);
      if (cls.equals("CDOTA_BaseNPC")) {
        Iterator<FieldPath> pit = e.getState().fieldPathIterator();
        StringBuilder sb = new StringBuilder();
        while (pit.hasNext()) {
          String pn = e.getNameForFieldPath(pit.next());
          if (UNIT_PROPS.matcher(pn).find() && !pn.startsWith("CBodyComponent"))
            sb.append(pn).append('=').append(String.valueOf((Object) e.getProperty(pn))).append(' ');
        }
        System.out.println("(c)   props: " + sb);
      }
      System.out.printf(
          "(c) tick=%d dt=%.2fs cast=%s class=%s name=%s pos=(%s,%s,%s) cell=(%s,%s) lifetime=%s%n",
          tick, (tick - c.tick()) / (double) TICKS_PER_SECOND, c.skill(), cls,
          prop(e, "m_iName"), prop(e, "CBodyComponent.m_vecX"), prop(e, "CBodyComponent.m_vecY"),
          prop(e, "CBodyComponent.m_vecZ"), prop(e, "CBodyComponent.m_cellX"),
          prop(e, "CBodyComponent.m_cellY"), lifetime(e));
      return;
    }
  }

  @OnEntityDeleted
  public void deleted(Context ctx, Entity e) {
    Integer born = watched.remove(e.getIndex());
    if (born != null)
      System.out.printf(
          "(c) deleted class=%s index=%d lived=%.2fs%n",
          e.getDtClass().getDtName(), e.getIndex(), (ctx.getTick() - born) / (double) TICKS_PER_SECOND);
  }

  static Object prop(Entity e, String name) {
    return e.hasProperty(name) ? e.getProperty(name) : "-";
  }

  static String lifetime(Entity e) {
    StringBuilder sb = new StringBuilder();
    Iterator<FieldPath> it = e.getState().fieldPathIterator();
    while (it.hasNext()) {
      String n = e.getNameForFieldPath(it.next());
      if (n.toLowerCase().contains("lifetime") || n.toLowerCase().contains("duration")
          || n.toLowerCase().contains("expire"))
        sb.append(n).append('=').append(String.valueOf((Object) e.getProperty(n))).append(' ');
    }
    return sb.length() == 0 ? "-" : sb.toString().trim();
  }

  void dumpHero(String attackerName) {
    String wanted = "CDOTA_Unit_Hero_" + heroClass(attackerName);
    String cls = wanted.replace("_", "").toLowerCase();
    entities.stream()
        .filter(e -> e.getDtClass().getDtName().replace("_", "").toLowerCase().equals(cls))
        .findFirst()
        .ifPresent(
            e -> {
              System.out.println("(d) hero entity class=" + cls);
              Iterator<FieldPath> it = e.getState().fieldPathIterator();
              while (it.hasNext()) {
                FieldPath fp = it.next();
                String n = e.getNameForFieldPath(fp);
                if (PROPS.matcher(n).find())
                  System.out.println("(d)   " + n + " = " + e.getPropertyForFieldPath(fp));
                else if (WIDER.matcher(n).find())
                  System.out.println("(d)   [wider] " + n + " = " + e.getPropertyForFieldPath(fp));
              }
            });
  }

  static String heroClass(String npcName) {
    String s = npcName.replace("npc_dota_hero_", "");
    StringBuilder sb = new StringBuilder();
    for (String part : s.split("_"))
      sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append('_');
    sb.setLength(sb.length() - 1);
    return sb.toString();
  }

  @OnTickStart
  public void tick(Context ctx, boolean synthetic) {
    if (synthetic) return;
    int tick = ctx.getTick();
    entities.stream()
        .filter(e -> e.getDtClass().getDtName().startsWith("CDOTA_Unit_Hero_"))
        .forEach(
            e -> {
              String key = e.getDtClass().getDtName();
              if (!e.hasProperty("CBodyComponent.m_cellX")) return;
              Object cell =
                  List.of(
                      e.getProperty("CBodyComponent.m_cellX"),
                      e.getProperty("CBodyComponent.m_cellY"),
                      prop(e, "CBodyComponent.m_vecX"),
                      prop(e, "CBodyComponent.m_vecY"));
              Object old = lastCell.put(key, cell);
              if (old != null && !old.equals(cell)) {
                updates.merge(key, 1, Integer::sum);
                Integer prev = lastSeen.put(key, tick);
                if (prev != null) gaps.merge(tick - prev, 1, Integer::sum);
              }
            });
  }

  public static void main(String[] args) throws Exception {
    var probe = new SkillCastProbe();
    new SimpleRunner(new MappedFileSource(args[0])).runWith(probe);
    System.out.println("(e) position-change gaps in ticks (gap -> count)");
    probe.gaps.forEach((k, v) -> System.out.println("(e)   " + k + "\t" + v));
    System.out.println("(e) position updates per hero: " + probe.updates);
    System.out.println("casts=" + probe.casts.size());
  }
}
