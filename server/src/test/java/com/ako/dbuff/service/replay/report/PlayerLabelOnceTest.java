package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerPhaseSummaryDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.PlayerPhaseSummaryRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A section that names a tracked player prints the label it is given once, never twice. */
class PlayerLabelOnceTest {
  private static final String LABEL = "Anton (Sven)";
  private static final long SLOT = 128L;

  private static final ReplayReportInput INPUT =
      new ReplayReportInput(1L, null, Set.of(SLOT), Map.of(SLOT, LABEL), 3);

  static Stream<Arguments> sections() {
    return Stream.of(
        Arguments.of(
            Named.<Supplier<List<String>>>of("composition", PlayerLabelOnceTest::composition)),
        Arguments.of(Named.<Supplier<List<String>>>of("deaths", PlayerLabelOnceTest::deaths)),
        Arguments.of(Named.<Supplier<List<String>>>of("draft", PlayerLabelOnceTest::draft)),
        Arguments.of(Named.<Supplier<List<String>>>of("fights", PlayerLabelOnceTest::fights)),
        Arguments.of(Named.<Supplier<List<String>>>of("movement", PlayerLabelOnceTest::movement)));
  }

  @ParameterizedTest
  @MethodSource("sections")
  void everyLineNamingThePlayerHasTheLabelExactlyOnce(Supplier<List<String>> section) {
    List<String> named = section.get().stream().filter(l -> l.contains("Anton")).toList();

    assertThat(named).isNotEmpty();
    assertThat(named)
        .allSatisfy(
            line -> {
              assertThat(line.split(java.util.regex.Pattern.quote(LABEL), -1)).hasSize(2);
              assertThat(line).doesNotContain("(Sven) (Sven)");
            });
  }

  private static List<String> composition() {
    PlayerRoleProfileRepo profiles = mock(PlayerRoleProfileRepo.class);
    TeamCompositionRepo compositions = mock(TeamCompositionRepo.class);
    when(profiles.findByMatchId(1L))
        .thenReturn(
            List.of(
                PlayerRoleProfileDomain.builder().playerSlot(SLOT).stage("FINAL").role(1).build()));
    when(compositions.findByMatchId(1L)).thenReturn(List.of());
    return new CompositionSection(profiles, compositions, new ReplayReportProperties())
        .lines(INPUT);
  }

  private static List<String> deaths() {
    DeathAnalysisRepo repo = mock(DeathAnalysisRepo.class);
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                DeathAnalysisDomain.builder()
                    .playerSlot(SLOT)
                    .deathSeconds(600.0)
                    .reason("TOWER")
                    .killerName("")
                    .build()));
    return new DeathSection(repo).lines(INPUT);
  }

  private static List<String> draft() {
    AbilityDraftPickRepo repo = mock(AbilityDraftPickRepo.class);
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                AbilityDraftPickDomain.builder()
                    .pickOrder(3)
                    .playerSlot(SLOT)
                    .abilityName("sven_storm_bolt")
                    .build()));
    return new DraftSection(repo, new AbilityDraftProfile(), TestLabels.labels()).lines(INPUT);
  }

  private static List<String> fights() {
    FightRepo fightRepo = mock(FightRepo.class);
    FightPlayerRepo partRepo = mock(FightPlayerRepo.class);
    FightAbilityUseRepo abilityRepo = mock(FightAbilityUseRepo.class);
    when(fightRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                FightDomain.builder()
                    .fightIndex(0)
                    .type("TEAMFIGHT")
                    .startSeconds(1390.0)
                    .radiantDeaths(4)
                    .direDeaths(1)
                    .winner("RADIANT")
                    .build()));
    when(partRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                FightPlayerDomain.builder()
                    .fightIndex(0)
                    .playerSlot(SLOT)
                    .presence("ABSENT_FARMING")
                    .absenceReason("NO_TP")
                    .build()));
    List<FightAbilityUseDomain> uses = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      uses.add(
          FightAbilityUseDomain.builder()
              .fightIndex(i)
              .playerSlot(SLOT)
              .abilityName("faceless_void_chronosphere")
              .nonCastReason("READY_NOT_CAST")
              .build());
    }
    when(abilityRepo.findByMatchId(1L)).thenReturn(uses);
    return new FightSection(fightRepo, partRepo, abilityRepo, Prettify::ability).lines(INPUT);
  }

  private static List<String> movement() {
    PlayerPhaseSummaryRepo repo = mock(PlayerPhaseSummaryRepo.class);
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                PlayerPhaseSummaryDomain.builder()
                    .playerSlot(SLOT)
                    .phase("LANE")
                    .startSeconds(0)
                    .endSeconds(720)
                    .deadSeconds(0)
                    .teleportingSeconds(0)
                    .inBaseSeconds(0)
                    .jungleFarmSeconds(0)
                    .laneFarmSeconds(600)
                    .pushingSeconds(0)
                    .travelingSeconds(0)
                    .positionedSeconds(0)
                    .fightsAvailable(0)
                    .fightsJoined(0)
                    .build()));
    return new MovementSection(repo).lines(INPUT);
  }
}
