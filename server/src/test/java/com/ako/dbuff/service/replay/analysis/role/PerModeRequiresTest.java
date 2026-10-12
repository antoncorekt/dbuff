package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisService;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The extractor an Ability Draft resolver needs must not hold back the roles of other modes. */
class PerModeRequiresTest {
  private static final long MATCH = 7L;

  private final PlayerRoleRepo roleRepo = mock(PlayerRoleRepo.class);
  private final PlayerRoleAnalyzer roles =
      new PlayerRoleAnalyzer(
          List.of(
              new AllPickRoleResolver(new ReplayAnalysisProperties()),
              new AbilityDraftRoleResolver(mock(StageProfilers.class), mock(RoleFitter.class))),
          mock(StoredMatchModes.class),
          mock(PlayerGameStatisticRepo.class),
          mock(PlayerTimelineRepo.class),
          mock(StoredActivity.class),
          roleRepo,
          mock(PlayerRoleProfileRepo.class),
          mock(AbilityDraftPickRepo.class),
          mock(StageProfilers.class),
          mock(RoleFitter.class));
  private final ReplayAnalyzer composition = mock(ReplayAnalyzer.class);
  private final ReplayAnalysisService service =
      new ReplayAnalysisService(List.of(roles, composition));

  @Test
  void eachModeNamesOnlyTheExtractorsOfItsOwnResolver() {
    assertThat(roles.requires(TestModes.ALL_PICK)).containsExactly("activity");
    assertThat(roles.requires(TestModes.ABILITY_DRAFT))
        .containsExactlyInAnyOrder("activity", "ability-draft");
  }

  @Test
  void aFailedDraftExtractorLeavesTheAllPickRolesAndWhatReadsThemAvailable() {
    when(composition.supports(any())).thenReturn(true);
    when(composition.requires(any())).thenReturn(Set.of("roles"));

    List<String> failed = service.analyze(MATCH, TestModes.ALL_PICK, List.of("ability-draft"));

    assertThat(failed).isEmpty();
    verify(roleRepo).deleteByMatchId(MATCH);
    verify(composition).analyze(MATCH);
  }

  @Test
  void aFailedDraftExtractorSkipsTheAbilityDraftRolesAndWhatReadsThem() {
    when(composition.supports(any())).thenReturn(true);
    when(composition.requires(any())).thenReturn(Set.of("roles"));
    when(composition.name()).thenReturn("composition");

    service.analyze(MATCH, TestModes.ABILITY_DRAFT, List.of("ability-draft"));

    verify(roleRepo, never()).deleteByMatchId(anyLong());
    verify(composition, never()).analyze(anyLong());
  }
}
