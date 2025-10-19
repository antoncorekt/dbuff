package com.ako.dbuff.service.details;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.ScrapperApiService;
import lombok.AllArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class DotaBuffMatchDetailsScrapper implements ScrapperService {

  private final ScrapperApiService scrapperApiService;

  @Override
  public Document scrap(MatchDomain matchDomain) {
    return scrapperApiService.scrap(
        String.format("https://www.dotabuff.com/matches/%s/builds", matchDomain.getId()));
  }
}
