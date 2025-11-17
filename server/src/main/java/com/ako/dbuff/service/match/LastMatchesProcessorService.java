package com.ako.dbuff.service.match;

import com.ako.dbuff.config.PlayerConfiguration;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class LastMatchesProcessorService {

  DotaApiLastMatchesService dotaApiLastMatchesService;
  MatchProcessorService matchProcessorService;
  MatchRepo matchRepo;

  public Set<Long> processLastMatches() {

    Set<Long> matchesToFetch =
        PlayerConfiguration.DEFAULT_PLAYERS.keySet().stream()
            .map(playerId -> dotaApiLastMatchesService.fetchLastMatches(playerId))
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

    log.info("Matches to fetch: {}", matchesToFetch);

    List<MatchDomain> matchDomains =
        matchesToFetch.stream()
            .map(
                id ->
                    matchRepo
                        .findById(id)
                        .orElseGet(() -> matchRepo.save(MatchDomain.builder().id(id).build())))
            .toList();

    matchProcessorService.process(matchDomains);

    return matchesToFetch;
  }
}
