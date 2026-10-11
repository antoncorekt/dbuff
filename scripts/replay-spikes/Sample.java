package spike;

import skadistats.clarity.event.Insert;
import skadistats.clarity.model.Entity;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;

@UsesEntities
public class Sample {
  @Insert Entities entities;
  static int at;
  boolean done;

  @OnTickStart
  public void tick(Context ctx, boolean synthetic) {
    if (done || ctx.getTick() < at) return;
    done = true;
    entities.stream().filter(Entities.byDtName("CDOTAPlayerController")).forEach(c -> {
      int pid = c.getProperty("m_nPlayerID");
      if (pid < 0) return;
      Entity hero = entities.getByHandle((Integer) c.getProperty("m_hAssignedHero"));
      if (hero == null) return;
      System.out.println("player " + pid + " team " + c.getProperty("m_iTeamNum") + " hero " + hero.getDtClass().getDtName()
        + " lvl " + hero.getProperty("m_iCurrentLevel") + " hp " + hero.getProperty("m_iHealth") + "/" + hero.getProperty("m_iMaxHealth")
        + " mana " + hero.getProperty("m_flMana") + " cell " + hero.getProperty("CBodyComponent.m_cellX") + "," + hero.getProperty("CBodyComponent.m_cellY")
        + " vec " + hero.getProperty("CBodyComponent.m_vecX") + "," + hero.getProperty("CBodyComponent.m_vecY") + " life " + hero.getProperty("m_lifeState"));
    });
    for (String dt : new String[] {"CDOTA_DataRadiant", "CDOTA_DataDire"}) {
      entities.stream().filter(Entities.byDtName(dt)).forEach(d -> {
        for (int i = 0; i < 5; i++) {
          String p = String.format("m_vecDataTeam.%04d.", i);
          System.out.println(dt + " " + i + " xp " + d.getProperty(p + "m_iTotalEarnedXP") + " nw " + d.getProperty(p + "m_iNetWorth")
            + " lh " + d.getProperty(p + "m_iLastHitCount") + " gold " + d.getProperty(p + "m_iTotalEarnedGold"));
        }
      });
    }
    entities.stream().filter(Entities.byDtName("CDOTA_PlayerResource")).forEach(r -> {
      for (int i = 0; i < 10; i++) {
        String p = String.format("m_vecPlayerTeamData.%04d.", i);
        System.out.println("resource " + i + " lvl " + r.getProperty(p + "m_iLevel") + " k " + r.getProperty(p + "m_iKills") + " d " + r.getProperty(p + "m_iDeaths"));
      }
    });
  }

  public static void main(String[] args) throws Exception {
    at = Integer.parseInt(args[1]);
    new SimpleRunner(new MappedFileSource(args[0])).runWith(new Sample());
  }
}
