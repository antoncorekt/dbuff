package com.ako.dbuff.service.scrapper;

import org.jsoup.nodes.Document;

/**
 * Scrapfly scraping provider.
 *
 * <p>API docs: https://scrapfly.io/docs/scrape-api/getting-started
 *
 * <p>Key parameters: key, url, render_js=true, asp=true (anti-bot). Response is JSON with HTML in
 * result.content. Base URL: https://api.scrapfly.io/scrape
 */
public class ScrapflyProvider implements ScrapperProvider {

  @Override
  public Document scrap(String targetUrl) {
    // TODO: implement Scrapfly scraping
    // GET https://api.scrapfly.io/scrape?key=API_KEY&url=ENCODED_URL&render_js=true&asp=true
    // Response JSON: { "result": { "content": "<html>...</html>" } }
    throw new UnsupportedOperationException("Scrapfly provider not yet implemented");
  }

  @Override
  public String name() {
    return "scrapfly";
  }
}
