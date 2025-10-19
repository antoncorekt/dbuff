package com.ako.dbuff.service.match.mapper;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.details.DotabuffBuildDetailsParser;
import com.ako.dbuff.service.details.ScrapperService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class DotabuffMatchMapperService {

  private final ScrapperService dotaBuffMatchDetailsScrapper;
  private final DotabuffBuildDetailsParser dotabuffBuildDetailsParser;
  private final MatchRepo matchRepo;

  public MatchDomain parse(Document document, MatchDomain matchDomain) {

    try {
      Document buildDetails = dotaBuffMatchDetailsScrapper.scrap(matchDomain);
      dotabuffBuildDetailsParser.parse(buildDetails, matchDomain);
    } catch (Exception e) {
      log.error("Build details dotabuff parse error.", e);
    }

    Elements select = document.select("div.footnote-container");

    String footerStr = select.stream().map(Element::text).collect(Collectors.joining());

    if (footerStr.contains("insignificant")) {
      matchDomain.setAbadon(true);
    }

    Elements headers = document.select("div.header-content-container");
    Element timeSelect = headers.select("time").first();

    if (timeSelect != null) {
      String datetime = timeSelect.attr("datetime");
      OffsetDateTime odt = OffsetDateTime.parse(datetime);

      LocalDateTime dateTime = odt.toLocalDateTime();
      matchDomain.setStartLocalDate(dateTime.toLocalDate());
      matchDomain.setStartYear(dateTime.getYear());
      matchDomain.setStartMonth(dateTime.getMonthValue());
      matchDomain.setStartTimeMillis(dateTime.toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    return matchRepo.save(matchDomain);
  }
}
