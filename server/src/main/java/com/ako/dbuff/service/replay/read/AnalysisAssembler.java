package com.ako.dbuff.service.replay.read;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Runs the requested section readers for a stored match and keys their output by name. */
@Service
public class AnalysisAssembler {
  private final Map<String, AnalysisSectionReader> readers;
  private final RunStatusReader statusReader;

  public AnalysisAssembler(List<AnalysisSectionReader> readers, RunStatusReader statusReader) {
    this.readers =
        readers.stream()
            .collect(
                Collectors.toMap(
                    AnalysisSectionReader::name,
                    Function.identity(),
                    (a, b) -> {
                      throw new IllegalStateException("Two readers for section " + a.name());
                    },
                    LinkedHashMap::new));
    this.statusReader = statusReader;
  }

  /**
   * Reads every requested section in one snapshot, so a re-analysis in between cannot mix two runs.
   *
   * @param sections comma separated section names, null for all
   * @return the sections in document order
   * @throws InvalidSectionsException when the list has an empty entry or a name that is not a
   *     section
   * @throws MatchNotStoredException when no replay run of the match is stored
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> assemble(long matchId, String sections) {
    List<String> wanted = requested(sections);
    RunStatus run =
        statusReader.read(matchId).orElseThrow(() -> new MatchNotStoredException(matchId));
    Map<String, Object> document = new LinkedHashMap<>();
    readers.values().stream()
        .filter(r -> wanted.contains(r.name()))
        .forEach(r -> document.put(r.name(), r.read(matchId, run)));
    return document;
  }

  private List<String> requested(String sections) {
    if (sections == null) {
      return List.copyOf(readers.keySet());
    }
    List<String> names = Arrays.stream(sections.split(",", -1)).map(String::trim).toList();
    if (names.stream().anyMatch(String::isEmpty)) {
      throw new InvalidSectionsException("sections has an empty entry: '" + sections + "'");
    }
    List<String> unknown = names.stream().filter(n -> !readers.containsKey(n)).toList();
    if (!unknown.isEmpty()) {
      throw new UnknownSectionException(unknown, List.copyOf(readers.keySet()));
    }
    return names;
  }
}
