package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * In Ability Draft the kit of a hero is made by the draft, so the hero says little. The roles are
 * worked out twice: INTENT from the hero and the skills each player holds, FINAL from what he did.
 * Each stage reads the profile of its {@link StageProfiler} and fits it with {@link RoleFitter}, so
 * a team gets each role once. The draft is read from the extractor {@code ability-draft}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbilityDraftRoleResolver implements RoleResolver {
  private static final int EVIDENCE_ROLES = 3;

  private final StageProfilers profilers;
  private final RoleFitter fitter;

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == AbilityDraftProfile.MODE_ID;
  }

  @Override
  public Set<RoleStage> stages() {
    return Set.of(RoleStage.INTENT, RoleStage.FINAL);
  }

  @Override
  public Set<String> requires() {
    return Set.of("ability-draft");
  }

  @Override
  public List<RoleAssignment> resolve(RoleContext context) {
    List<FunctionProfile> profiles = profilers.of(context.stage()).profile(context.team());
    if (profiles.stream().allMatch(FunctionProfile.UNKNOWN::equals)) {
      log.info("Nothing known of the {} of slots {}, no roles", context.stage(), slots(context));
      return List.of();
    }
    List<RoleFitter.RoleFit> fits = fitter.fit(profiles);
    List<RoleAssignment> result = new ArrayList<>();
    for (int i = 0; i < fits.size(); i++) {
      RoleFitter.RoleFit fit = fits.get(i);
      result.add(
          new RoleAssignment(
              context.team().get(i).slot(),
              fit.role(),
              PlayerRoleSource.DERIVED,
              fit.confidence(),
              evidence(context.stage(), fit.scores())));
    }
    return result;
  }

  private static List<Long> slots(RoleContext context) {
    return context.team().stream().map(RolePlayer::slot).toList();
  }

  private static String evidence(RoleStage stage, double[] scores) {
    String best =
        IntStream.range(0, scores.length)
            .boxed()
            .sorted(Comparator.comparingDouble((Integer i) -> scores[i]).reversed())
            .limit(EVIDENCE_ROLES)
            .map(i -> String.format(Locale.ROOT, "%d(%.2f)", i + 1, scores[i]))
            .collect(Collectors.joining(" "));
    return stage.name().toLowerCase(Locale.ROOT) + " fit, best roles by score: " + best;
  }
}
