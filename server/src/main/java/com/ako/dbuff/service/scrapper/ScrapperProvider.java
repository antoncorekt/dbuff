package com.ako.dbuff.service.scrapper;

import org.jsoup.nodes.Document;

/** Abstraction for web scraping providers. */
public interface ScrapperProvider {

  Document scrap(String targetUrl);

  String name();
}
