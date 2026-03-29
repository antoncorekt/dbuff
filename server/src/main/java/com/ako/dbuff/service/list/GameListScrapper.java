package com.ako.dbuff.service.list;

import com.ako.dbuff.service.ScrapperApiService;
import lombok.AllArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class GameListScrapper {
  private final ScrapperApiService scrapperApiService;

  public Document scrap(Long userId, Integer page) {

    String url =
        String.format(
            "https://www.dotabuff.com/players/%d/matches?enhance=overview&page=%d", userId, page);

    return scrapperApiService.scrap(url);
  }
}
