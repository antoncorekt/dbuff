package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CastSnapshot;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.Vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CastSnapshotRecorderTest {
  private static final Set<String> SKILLS = Set.of("pudge_meat_hook");

  private final Entity pudge = hero(138, 134, 247.5f, 0);
  private final Entity doom = hero(100, 100, 10f, 1);
  private final CastSnapshotRecorder recorder =
      new CastSnapshotRecorder(
          () ->
              List.of(new Controllers.View(0, 2, 1L, pudge), new Controllers.View(5, 3, 2L, doom)),
          e -> e == pudge ? "Pudge" : "DoomBringer",
          SKILLS);

  @Test
  void cast_recordsAFrameAtTheCastAndEverySixTicksForFiveSeconds() {
    recorder.cast("pudge_meat_hook", "npc_dota_hero_pudge", 211.1, 1000);
    for (long tick = 1001; tick <= 1200; tick++) {
      recorder.tick(tick);
    }

    List<CastSnapshot> frames = recorder.result(100, 11.1).snapshots();

    assertThat(frames).hasSize(26);
    assertThat(frames.getFirst().castSecond()).isEqualTo(200.0, within(1e-9));
    assertThat(frames.getFirst().second()).isEqualTo(30.0);
    assertThat(frames.get(1).second()).isEqualTo(30.2, within(1e-9));
    assertThat(frames.getLast().second()).isEqualTo(35.0);
    CastSnapshot first = frames.getFirst();
    assertThat(first.casterId()).isZero();
    assertThat(first.facing()).isEqualTo(248);
    assertThat(first.heroes()).hasSize(2);
    assertThat(first.heroes().getFirst())
        .isEqualTo(new CastSnapshot.HeroPoint(0, 10 * 128 + 41, 6 * 128 + 41, true));
    assertThat(first.heroes().get(1).alive()).isFalse();
  }

  @Test
  void cast_ignoresASkillThatIsNotCatalogued_andACasterWithoutAHero() {
    recorder.cast("tusk_walrus_punch", "npc_dota_hero_pudge", 1, 10);
    recorder.cast("pudge_meat_hook", "npc_dota_hero_axe", 1, 10);
    recorder.tick(20);

    assertThat(recorder.result(0, 0).snapshots()).isEmpty();
  }

  @Test
  void cast_keepsTheCasterWhenTheHeroNameHasNoUnderscore() {
    recorder.cast("pudge_meat_hook", "npc_dota_hero_doom_bringer", 1, 10);

    assertThat(recorder.result(0, 0).snapshots().getFirst().casterId()).isEqualTo(5);
    assertThat(recorder.result(0, 0).snapshots().getFirst().facing()).isEqualTo(10);
  }

  private static Entity hero(int cellX, int cellY, float yaw, int lifeState) {
    Entity e = mock(Entity.class);
    prop(e, "CBodyComponent.m_cellX", cellX);
    prop(e, "CBodyComponent.m_cellY", cellY);
    prop(e, "CBodyComponent.m_vecX", 41f);
    prop(e, "CBodyComponent.m_vecY", 41f);
    prop(e, "m_lifeState", lifeState);
    prop(e, "CBodyComponent.m_angRotation", new Vector(0f, yaw, 0f));
    return e;
  }

  private static void prop(Entity e, String name, Object value) {
    when(e.hasProperty(name)).thenReturn(true);
    when(e.getProperty(name)).thenReturn(value);
  }
}
