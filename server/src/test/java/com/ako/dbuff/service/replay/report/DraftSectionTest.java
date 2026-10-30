package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.report.chart.ChartPalette;
import com.ako.dbuff.service.replay.report.chart.SkillIconImages;
import com.ako.dbuff.service.replay.report.chart.TableRenderer;
import com.ako.dbuff.service.replay.report.chart.TableSpec;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DraftSectionTest {
  private static final byte[] PNG = {1, 2, 3};

  private final AbilityDraftPickRepo repo = mock(AbilityDraftPickRepo.class);
  private final PlayerGameStatisticRepo stats = mock(PlayerGameStatisticRepo.class);
  private final HeroKits kits = mock(HeroKits.class);
  private final SkillIconImages icons = mock(SkillIconImages.class);
  private final TableRenderer tables = mock(TableRenderer.class);
  private final DraftSection section =
      new DraftSection(
          repo, stats, new AbilityDraftProfile(), Prettify::ability, kits, icons, tables);

  DraftSectionTest() {
    when(tables.render(any())).thenReturn(Optional.of(PNG));
    when(icons.ability(any())).thenReturn(Optional.empty());
    when(icons.hero(anyLong())).thenReturn(Optional.empty());
    when(kits.hero(anyLong())).thenReturn(Optional.empty());
    when(stats.findAllByMatchId(anyLong())).thenReturn(List.of());
  }

  private AbilityDraftPickDomain pick(int order, long slot, String ability) {
    return AbilityDraftPickDomain.builder()
        .pickOrder(order)
        .turnNumber(order + 1)
        .teamPickIndex((order + 1) / 2)
        .playerSlot(slot)
        .abilityName(ability)
        .build();
  }

  private static AbilityDraftPickDomain noTurn(int order, long slot, String ability) {
    return AbilityDraftPickDomain.builder()
        .pickOrder(order)
        .playerSlot(slot)
        .abilityName(ability)
        .build();
  }

  private static PlayerMatchStatisticDomain played(long slot, long heroId) {
    return PlayerMatchStatisticDomain.builder().playerSlot(slot).heroId(heroId).build();
  }

  private static HeroConstant hero(String name) {
    return HeroConstant.builder().localized_name(name).build();
  }

  private static ReplayReportInput input(Map<Long, String> names) {
    return new ReplayReportInput(1L, null, names.keySet(), names, 3);
  }

  private static ReplayReportInput anton() {
    return input(Map.of(0L, "Anton"));
  }

  @Test
  void lines_listOneBulletPerSkillUnderEachTrackedPlayerInPickOrder() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                pick(12, 0, "sven_gods_strength"),
                pick(3, 0, "faceless_void_chronosphere"),
                pick(22, 0, "sven_storm_bolt"),
                pick(1, 128, "axe_culling_blade")));

    assertThat(section.lines(anton()))
        .containsExactly(
            "**Draft**",
            "Anton",
            "- Faceless Void Chronosphere · pick 4",
            "- Sven Gods Strength · pick 13",
            "- Sven Storm Bolt · pick 23");
  }

  @Test
  void lines_nameACrossHeroPickByItsAbilityNotItsSourceHero() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(pick(3, 0, "terrorblade_metamorphosis"), pick(12, 0, "sven_gods_strength")));
    DraftSection real =
        new DraftSection(
            repo, stats, new AbilityDraftProfile(), TestLabels.labels(), kits, icons, tables);

    assertThat(real.lines(anton()))
        .containsExactly(
            "**Draft**", "Anton", "- Metamorphosis · pick 4", "- Gods Strength · pick 13");
  }

  @Test
  void lines_areEmptyWithoutADraft() {
    when(repo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.lines(anton())).isEmpty();
    assertThat(section.view(anton())).isEmpty();
    verify(tables, never()).render(any());
  }

  @Test
  void lines_fallBackToTheTeamPickWhenTheOverallOrderIsNullAndSortThoseLast() {
    AbilityDraftPickDomain unordered =
        AbilityDraftPickDomain.builder()
            .teamPickIndex(2)
            .playerSlot(0L)
            .abilityName("sven_storm_bolt")
            .build();
    AbilityDraftPickDomain bare =
        AbilityDraftPickDomain.builder().playerSlot(0L).abilityName("sven_great_cleave").build();
    when(repo.findByMatchId(1L))
        .thenReturn(List.of(unordered, bare, noTurn(5, 0, "sven_gods_strength")));

    assertThat(section.lines(anton()))
        .containsExactly(
            "**Draft**",
            "Anton",
            "- Sven Gods Strength · pick 5",
            "- Sven Storm Bolt · team pick 2",
            "- Sven Great Cleave · ");
  }

  @Test
  void view_carriesTheVerdictAndThePictureAndPlainTextMatchesIt() {
    when(repo.findByMatchId(1L))
        .thenReturn(List.of(pick(12, 0, "sven_gods_strength"), pick(3, 0, "sven_storm_bolt")));
    ReplayReportInput in = anton();

    SectionView view = section.view(in).orElseThrow();

    ViewRendering.assertWithinLimits(view);
    assertThat(view.title()).isEqualTo("Draft");
    assertThat(view.verdict()).isEqualTo("Ability picks of 1 tracked player");
    assertThat(view.accent()).isEqualTo(Accent.NEUTRAL);
    assertThat(view.image().name()).isEqualTo("draft-1.png");
    assertThat(view.image().bytes()).isEqualTo(PNG);
    assertThat(view.bullets())
        .containsExactly("Anton", "- Sven Storm Bolt · pick 4", "- Sven Gods Strength · pick 13");
    assertThat(new PlainViewRenderer().lines(view)).isEqualTo(section.lines(in));
    assertThat(section.image(in).orElseThrow().name()).isEqualTo("draft-1.png");
  }

  @Test
  void view_drawsOneRowPerTrackedPlayerWithHeroTeamBandAndNumberedSkills() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                pick(5, 129, "axe_counter_helix"),
                pick(14, 129, "axe_culling_blade"),
                pick(29, 129, "pangolier_lucky_shot"),
                pick(37, 129, "earth_spirit_boulder_smash"),
                pick(6, 130, "troll_warlord_fervor"),
                pick(7, 131, "earth_spirit_geomagnetic_grip"),
                pick(8, 132, "meepo_earthbind")));
    when(stats.findAllByMatchId(1L)).thenReturn(List.of(played(129L, 18L)));
    when(kits.hero(18L)).thenReturn(Optional.of(hero("Phoenix")));
    when(icons.hero(18L)).thenReturn(Optional.of(new byte[] {7}));
    when(icons.ability("axe_counter_helix")).thenReturn(Optional.of(new byte[] {8}));

    section
        .view(
            input(
                new TreeMap<>(
                    Map.of(129L, "Пастух", 130L, "Doctor", 131L, "Lolets", 132L, "Neurotic"))))
        .orElseThrow();

    TableSpec drawn = drawn();
    assertThat(drawn.rows()).hasSize(4);
    assertThat(drawn.headers())
        .containsExactly("Player", "Hero", "Skill 1", "Skill 2", "Skill 3", "Skill 4");
    TableSpec.Cell player = drawn.rows().get(0).get(0);
    assertThat(player.text()).isEqualTo("Пастух");
    assertThat(player.icon()).containsExactly(7);
    assertThat(player.band()).isEqualTo(ChartPalette.DIRE);
    assertThat(drawn.rows().get(0).get(1).text()).isEqualTo("Phoenix");
    TableSpec.Cell first = drawn.rows().get(0).get(2);
    assertThat(first.text()).isEqualTo("Axe Counter Helix");
    assertThat(first.note()).isEqualTo("#6");
    assertThat(first.icon()).containsExactly(8);
  }

  @Test
  void view_takesEachPlayersHeroFromTheMatchAndTheSkillsFromTheirSlotWhateverTheHeroPickOrder() {
    when(repo.findByMatchId(1L))
        .thenReturn(List.of(pick(6, 130, "troll_warlord_fervor"), pick(8, 132, "meepo_earthbind")));
    when(stats.findAllByMatchId(1L)).thenReturn(List.of(played(132L, 94L), played(130L, 95L)));
    when(kits.hero(94L)).thenReturn(Optional.of(hero("Meepo")));
    when(kits.hero(95L)).thenReturn(Optional.of(hero("Troll Warlord")));

    section.view(input(new TreeMap<>(Map.of(130L, "Doctor", 132L, "Neurotic")))).orElseThrow();

    TableSpec drawn = drawn();
    assertThat(drawn.rows().get(0).get(0).text()).isEqualTo("Doctor");
    assertThat(drawn.rows().get(0).get(1).text()).isEqualTo("Troll Warlord");
    assertThat(drawn.rows().get(0).get(2).text()).isEqualTo("Troll Warlord Fervor");
    assertThat(drawn.rows().get(1).get(0).text()).isEqualTo("Neurotic");
    assertThat(drawn.rows().get(1).get(1).text()).isEqualTo("Meepo");
    assertThat(drawn.rows().get(1).get(2).text()).isEqualTo("Meepo Earthbind");
  }

  @Test
  void view_fallsBackToTheTextWithAnApologyWhenThePictureFails() {
    when(repo.findByMatchId(1L)).thenReturn(List.of(pick(3, 0, "sven_storm_bolt")));
    when(tables.render(any())).thenReturn(Optional.empty());

    SectionView view = section.view(anton()).orElseThrow();

    assertThat(view.image()).isNull();
    assertThat(view.bullets())
        .containsExactly(
            "Anton", "- Sven Storm Bolt · pick 4", "The picture could not be drawn this time.");
    assertThat(section.image(anton())).isEmpty();
  }

  @Test
  void view_fallsBackWhenDrawingThrows() {
    when(repo.findByMatchId(1L)).thenReturn(List.of(pick(3, 0, "sven_storm_bolt")));
    when(tables.render(any())).thenThrow(new IllegalStateException("boom"));

    assertThat(section.view(anton()).orElseThrow().bullets())
        .contains("The picture could not be drawn this time.");
  }

  @Test
  void linesAndImageOfOneInputDrawThePictureOnceAndImageIsTheViewsImage() {
    when(repo.findByMatchId(1L)).thenReturn(List.of(pick(3, 0, "sven_storm_bolt")));
    ReplayReportInput in = anton();

    section.lines(in);
    var image = section.image(in);

    verify(tables, times(1)).render(any());
    assertThat(image).isEqualTo(Optional.of(section.view(in).orElseThrow().image()));
  }

  private TableSpec drawn() {
    ArgumentCaptor<TableSpec> spec = ArgumentCaptor.forClass(TableSpec.class);
    verify(tables).render(spec.capture());
    return spec.getValue();
  }
}
