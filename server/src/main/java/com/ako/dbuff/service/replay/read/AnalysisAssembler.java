package com.ako.dbuff.service.replay.read;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

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
   * @param sections comma separated section names, blank or null for all
   * @return the sections in document order, empty when no replay run of the match is stored
   * @throws UnknownSectionException when a name is not a section
   */
  public Optional<Map<String, Object>> assemble(long matchId, String sections) {
    List<String> wanted = requested(sections);
    if (statusReader.read(matchId).isEmpty()) {
      return Optional.empty();
    }
    Map<String, Object> document = new LinkedHashMap<>();
    readers.values().stream()
        .filter(r -> wanted.contains(r.name()))
        .forEach(r -> document.put(r.name(), r.read(matchId)));
    return Optional.of(document);
  }

  private List<String> requested(String sections) {
    if (sections == null || sections.isBlank()) {
      return List.copyOf(readers.keySet());
    }
    List<String> names =
        Arrays.stream(sections.split(",")).map(String::trim).filter(n -> !n.isEmpty()).toList();
    List<String> unknown = names.stream().filter(n -> !readers.containsKey(n)).toList();
    if (!unknown.isEmpty()) {
      throw new UnknownSectionException(unknown, List.copyOf(readers.keySet()));
    }
    return names;
  }
}
