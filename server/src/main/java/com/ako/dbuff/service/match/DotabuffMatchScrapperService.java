package com.ako.dbuff.service.match;

import com.ako.dbuff.service.ScrapperApiService;
import lombok.AllArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class DotabuffMatchScrapperService {

  private final ScrapperApiService scrapperApiService;

  public Document scrap(Long matchId) {
    return scrapperApiService.scrap(String.format("https://www.dotabuff.com/matches/%d", matchId));
  }
}
