package com.ako.dbuff.service.list;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.match.MatchProcessorService;
import com.ako.dbuff.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class AllHistoryService {

  private final GameListScrapper gameListScrapper;
  private final GameListParser gameListParser;
  private final MatchProcessorService matchProcessorService;

  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  @Builder
  public static class ScrapperParams {
    int startPage;
    int endPage;
  }

  public List<MatchDomain> findAllUserMatches(
      long userId, long gameMode, ScrapperParams scrapperParams) {

    List<MatchDomain> allUserMatches = new ArrayList<>();

    int startPage = Utils.nvl(scrapperParams.getStartPage(), 0);
    int endPage = Utils.nvl(scrapperParams.getEndPage(), -1);

    do {
      startPage = startPage + 1;
      log.info("Start find all matches for player {} - page {}", userId, startPage);
      Document document = gameListScrapper.scrap(userId, startPage);
      List<MatchDomain> matchesOnPage = gameListParser.parse(document);

      log.info("Found {} matches on page {} for user {}", matchesOnPage.size(), startPage, userId);
      allUserMatches.addAll(matchesOnPage);

      if (endPage == -1) {
        endPage = findEndPage(document);
        log.info("Found end page cound - {} for userId {}", endPage, userId);
      }

      log.info("start processing all matches for player {} - page {}", userId, startPage);
      matchProcessorService.process(matchesOnPage);

    } while (startPage <= endPage);

    return allUserMatches;
  }

  private int findEndPage(Document document) {
    Element lastSpan = document.select("span.last").getFirst();

    String href = lastSpan.child(0).attr("href");

    return Integer.parseInt(href.substring(href.lastIndexOf("page=") + 5));
  }
}
