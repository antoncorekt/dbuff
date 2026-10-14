package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerPhaseSummaryDomain;
import com.ako.dbuff.dao.repo.PlayerPhaseSummaryRepo;
import com.ako.dbuff.service.replay.report.view.ComponentsViewRenderer;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MovementSectionTest {

  private final PlayerPhaseSummaryRepo repo = mock(PlayerPhaseSummaryRepo.class);
  private final MovementSection section = new MovementSection(repo);

  @Test
  void lines_sayWhereTheTimeWentAndHowManyFightsWereJoined() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                PlayerPhaseSummaryDomain.builder()
                    .playerSlot(0L)
                    .phase("MID")
                    .startSeconds(720)
                    .endSeconds(1500)
                    .deadSeconds(60)
                    .teleportingSeconds(0)
                    .inBaseSeconds(40)
                    .jungleFarmSeconds(480)
                    .laneFarmSeconds(100)
                    .pushingSeconds(0)
                    .travelingSeconds(100)
                    .positionedSeconds(0)
                    .fightsAvailable(7)
                    .fightsJoined(3)
                    .goldPerMinute(612.4)
                    .build()));

    List<String> lines =
        section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3));

    assertThat(lines)
        .containsExactly(
            "**Movement**",
            "Anton",
            "- 12-25 min: 62% farming the jungle, 13% farming a lane, joined 3 of 7 fights, 612"
                + " gold a minute");
  }

  private static PlayerPhaseSummaryDomain phase(long slot, String name, int from, int to) {
    return PlayerPhaseSummaryDomain.builder()
        .playerSlot(slot)
        .phase(name)
        .startSeconds(from)
        .endSeconds(to)
        .deadSeconds(0)
        .teleportingSeconds(0)
        .inBaseSeconds(0)
        .jungleFarmSeconds(0)
        .laneFarmSeconds(to - from)
        .pushingSeconds(0)
        .travelingSeconds(0)
        .positionedSeconds(0)
        .fightsAvailable(0)
        .fightsJoined(0)
        .build();
  }

  @Test
  void lines_nameEachPlayerOnceAboveHisPhases() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                phase(0L, "LATE", 1500, 2400),
                phase(128L, "LANE", 0, 720),
                phase(0L, "LANE", 0, 720),
                phase(0L, "MID", 720, 1500)));

    List<String> lines =
        section.lines(
            new ReplayReportInput(
                1L, null, Set.of(0L, 128L), new LinkedHashMap<>(orderedNames()), 3));

    assertThat(lines)
        .containsExactly(
            "**Movement**",
            "Anton (Sven)",
            "- 0-12 min: 100% farming a lane",
            "- 12-25 min: 100% farming a lane",
            "- 25-40 min: 100% farming a lane",
            "Boris (Axe)",
            "- 0-12 min: 100% farming a lane");
  }

  @Test
  void view_isOneBlockPerPlayerWithItsPhasesOnSeparateLines() {
    when(repo.findByMatchId(1L))
        .thenReturn(List.of(phase(0L, "LANE", 0, 720), phase(0L, "MID", 720, 1500)));

    SectionView view =
        section
            .view(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton (Sven)"), 3))
            .orElseThrow();

    assertThat(view.title()).isEqualTo("Movement");
    assertThat(view.bullets())
        .containsExactly(
            "Anton (Sven)\n- 0-12 min: 100% farming a lane\n- 12-25 min: 100% farming a lane");
  }

  @Test
  void bothRenderersShowThePhasesUnderThePlayerWithTheirMarker() {
    when(repo.findByMatchId(1L))
        .thenReturn(List.of(phase(0L, "LANE", 0, 720), phase(0L, "MID", 720, 1500)));
    SectionView view =
        section
            .view(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton (Sven)"), 3))
            .orElseThrow();
    PlainViewRenderer plain = new PlainViewRenderer();

    String expected =
        "Anton (Sven)\n- 0-12 min: 100% farming a lane\n- 12-25 min: 100% farming a lane";
    assertThat(plain.render(view))
        .singleElement()
        .extracting(Object::toString)
        .asString()
        .contains(expected);
    ReportMessage.Components card =
        (ReportMessage.Components) new ComponentsViewRenderer(plain).render(view).getFirst();
    assertThat(card.fallback()).contains(expected);
    Container container = (Container) card.components().get(0);
    assertThat(container.getComponents())
        .filteredOn(TextDisplay.class::isInstance)
        .map(c -> ((TextDisplay) c).getContent())
        .contains(expected);
  }

  @Test
  void view_isEmptyWithoutSummaries() {
    when(repo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.view(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3)))
        .isEmpty();
  }

  private static Map<Long, String> orderedNames() {
    Map<Long, String> names = new LinkedHashMap<>();
    names.put(0L, "Anton (Sven)");
    names.put(128L, "Boris (Axe)");
    return names;
  }

  @Test
  void lines_areEmptyWithoutSummaries() {
    when(repo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3)))
        .isEmpty();
  }
}
