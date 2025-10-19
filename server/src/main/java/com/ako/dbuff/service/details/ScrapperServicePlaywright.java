package com.ako.dbuff.service.details;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.List;
import java.util.Random;
import lombok.AllArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ScrapperServicePlaywright {

  private final PlayerRepo playerRepo;

  public Document scrap(MatchDomain matchDomain) {
    try (Playwright playwright = Playwright.create()) {
      // Randomize User-Agent
      String[] userAgents =
          new String[] {
            // Windows Chrome
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.188 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.5790.217 Safari/537.36",
            // Windows Edge
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Edge/116.0.1938.81",
            // Mac Chrome
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.188 Safari/537.36",
            // Mac Safari
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Safari/605.1.15",
            // Linux Chrome
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.5790.171 Safari/537.36",
            // Mobile Safari iPhone
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Mobile/15E148 Safari/604.1",
            // Mobile Chrome Android
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.188 Mobile Safari/537.36",
            // iPad Safari
            "Mozilla/5.0 (iPad; CPU OS 16_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Mobile/15E148 Safari/604.1",
            // Linux Firefox
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:115.0) Gecko/20100101 Firefox/115.0"
          };

      String userAgent = userAgents[new Random().nextInt(userAgents.length)];

      // Randomize viewport size
      int[] widths = {1200, 1280, 1366, 1440, 1536, 1600};
      int[] heights = {700, 768, 800, 900, 900, 1024};
      double[] scaleFactors = {1.0, 1.25, 1.5, 1.75, 2.0, 2.5};

      int width = widths[new Random().nextInt(widths.length)];
      int height = heights[new Random().nextInt(heights.length)];
      double deviceScaleFactor = scaleFactors[new Random().nextInt(scaleFactors.length)];

      String[][] languages = {
        {"en-US", "en"}, {"en-GB", "en"}, {"fr-FR", "fr", "en"}, {"de-DE", "de", "en"}
      };
      String[] navLang = languages[new Random().nextInt(languages.length)];

      int[][] pluginCounts = {{1, 2, 3}, {2, 3, 4, 5}, {5, 6, 7}};
      int[] plugins = pluginCounts[new Random().nextInt(pluginCounts.length)];

      Browser browser =
          playwright
              .chromium()
              .launch(
                  new BrowserType.LaunchOptions()
                      .setHeadless(true)
                      .setArgs(List.of("--headless=new")));

      BrowserContext context =
          browser.newContext(
              new Browser.NewContextOptions()
                  .setUserAgent(userAgent)
                  .setDeviceScaleFactor(deviceScaleFactor)
                  .setViewportSize(width, height));

      Page page = context.newPage();

      // Inject stealth JS
      page.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");
      page.addInitScript("window.chrome = { runtime: {} };");
      page.addInitScript(
          "const getParameter = WebGLRenderingContext.prototype.getParameter;"
              + "WebGLRenderingContext.prototype.getParameter = function(param) {"
              + "  if (param === 37445) return 'Intel Inc.';"
              + // UNMASKED_VENDOR_WEBGL
              "  if (param === 37446) return 'Intel Iris OpenGL Engine';"
              + // UNMASKED_RENDERER_WEBGL
              "  return getParameter.call(this, param);"
              + "};");
      page.addInitScript(
          "Object.defineProperty(navigator, 'languages', {get: () => "
              + java.util.Arrays.toString(navLang)
              + "});");
      page.addInitScript(
          "Object.defineProperty(navigator, 'plugins', {get: () => "
              + java.util.Arrays.toString(plugins)
              + "});");

      // Navigate to Dotabuff
      page.navigate(
          String.format("https://www.dotabuff.com/matches/%s/builds", matchDomain.getId()));

      // Wait for Cloudflare to complete
      int delay = 4000 + new Random().nextInt(9000); // 2–7 sec
      page.waitForTimeout(delay);

      // Then wait for the builds section
      page.waitForSelector("section.performance-artifact");

      // Extract the HTML
      String content = page.content();

      browser.close();

      return Jsoup.parse(content);
    }
  }
}
