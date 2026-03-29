package com.ako.dbuff.service.scrapper;

import org.jsoup.nodes.Document;

/**
 * ZenRows scraping provider.
 *
 * <p>API docs: https://docs.zenrows.com/scraper-api/overview
 *
 * <p>Key parameters: apikey, url, js_render=true, premium_proxy=true. Response is raw HTML. Base
 * URL: https://api.zenrows.com/v1/
 */
public class ZenRowsProvider implements ScrapperProvider {

  @Override
  public Document scrap(String targetUrl) {
    // TODO: implement ZenRows scraping
    // GET
    // https://api.zenrows.com/v1/?apikey=API_KEY&url=ENCODED_URL&js_render=true&premium_proxy=true
    // Response: raw HTML
    throw new UnsupportedOperationException("ZenRows provider not yet implemented");
  }

  @Override
  public String name() {
    return "zenrows";
  }
}
