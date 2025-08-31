package com.skybetscraper;

import com.microsoft.playwright.*;

import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class BundesligaScraper {
  public static void main(String[] args) {
  // Modes:
  // - No args: fetch first 9 Bundesliga event URLs from the competition page and print
  // - Arg is an http(s) URL: treat as a match page and scrape Correct Score odds
  String mode = args.length == 0 ? "list" : "scrape";
  String url = args.length > 0 ? args[0] : "https://skybet.com/football/german-bundesliga/c-59";

    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(
        new BrowserType.LaunchOptions().setHeadless(true)
      );
      BrowserContext context = browser.newContext();
      Page page = context.newPage();

      System.out.println("Navigating to: " + url);
      page.navigate(url);
      page.waitForLoadState();

      // Cookie consent: click Allow Necessary Only
      try { page.locator("#onetrust-reject-all-handler").click(new Locator.ClickOptions().setTimeout(8000)); } catch (Throwable ignored) {}
      page.waitForTimeout(500);

      if ("list".equals(mode)) {
        // Fetch first 9 Bundesliga event URLs from the competition page
        @SuppressWarnings("unchecked")
        // Wait until preloaded catalog or anchors are present
        Object __wait = null;
        try {
          __wait = page.waitForFunction(
            "() => {\n" +
            "  const c = window.__TBD_PRELOADED_CATALOG__;\n" +
            "  if (c && c.data && (Array.isArray(c.data.EventMarketCard) || Array.isArray(c.data.FilteredCouponCardGroup))) return true;\n" +
            "  return !!document.querySelector('a[href]');\n" +
            "}",
            new Page.WaitForFunctionOptions().setTimeout(15000)
          );
        } catch (Throwable ignored) {}

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) page.evaluate(
          "() => {\n" +
          "  const prepend = (u) => { if (!u) return null; if (/^https?:/i.test(u)) return u; return 'https://skybet.com/' + (u.startsWith('/') ? u.slice(1) : u); };\n" +
          "  const out = []; const seen = new Set();\n" +
          "  try {\n" +
          "    const cat = window.__TBD_PRELOADED_CATALOG__;\n" +
          "    const arr = cat && cat.data && Array.isArray(cat.data.EventMarketCard) ? cat.data.EventMarketCard : null;\n" +
          "    if (arr) {\n" +
          "      const re = new RegExp('(^|/)football/german-bundesliga/.+/e-\\\\d+');\n" +
          "      for (const it of arr) {\n" +
          "        const v = it && it.eventViewLink && it.eventViewLink.viewUrl;\n" +
          "        if (typeof v === 'string' && re.test(v)) {\n" +
          "          const full = prepend(v); if (full && !seen.has(full)) { seen.add(full); out.push(full); if (out.length >= 9) break; }\n" +
          "        }\n" +
          "      }\n" +
          "    }\n" +
          "  } catch (e) {}\n" +
          "  if (out.length === 0) {\n" +
          "    const anchors = Array.from(document.querySelectorAll('a[href]'));\n" +
          "    const re2 = new RegExp('(^|/)football/german-bundesliga/.+/e-\\\\d+');\n" +
          "    for (const a of anchors) { const href = a.getAttribute('href') || ''; if (re2.test(href)) { const full = prepend(href); if (full && !seen.has(full)) { seen.add(full); out.push(full); if (out.length >= 9) break; } } }\n" +
          "  }\n" +
          "  return out;\n" +
          "}"
        );

        if (urls == null || urls.isEmpty()) {
          System.out.println("No Bundesliga event URLs found.");
          browser.close();
          return;
        } else {
          System.out.println("First 9 Bundesliga event URLs:");
          for (String u : urls) System.out.println(u);
          // Loop over URLs and scrape odds for each
      for (String matchUrl : urls) {
            try {
              page.navigate(matchUrl);
              page.waitForLoadState();
              try { page.locator("#onetrust-reject-all-handler").click(new Locator.ClickOptions().setTimeout(5000)); } catch (Throwable ignored) {}
              page.waitForTimeout(400);
        printEventHeader(page);
              scrapeCorrectScoreOdds(page);
            } catch (Throwable t) {
              System.out.println("Failed to scrape " + matchUrl + ": " + t.getMessage());
            }
          }
          browser.close();
          return;
        }
      }
      // Single scrape mode: current page is an event page
      printEventHeader(page);
      scrapeCorrectScoreOdds(page);
      page.waitForTimeout(800);
      browser.close();
    }
  }

  private static void printEventHeader(Page page) {
    @SuppressWarnings("unchecked")
    Map<String, Object> meta = (Map<String, Object>) page.evaluate(
      "() => {\n" +
      "  let name = null, dateIso = null;\n" +
      "  try {\n" +
      "    const cat = window.__TBD_PRELOADED_CATALOG__;\n" +
      "    const arr = cat && cat.data && Array.isArray(cat.data.SportsEvent) ? cat.data.SportsEvent : null;\n" +
      "    if (arr && arr.length) {\n" +
      "      const se = arr.find(x => x && typeof x.name==='string' && typeof x.openDate==='string') || arr[0];\n" +
      "      if (se) { name = se.name; dateIso = se.openDate; }\n" +
      "    }\n" +
      "  } catch(e) {}\n" +
      "  if (!name) {\n" +
      "    const cand = [...document.querySelectorAll('h1,h2,h3')].map(n=>n.textContent&&n.textContent.trim()).find(t=>t && /(\\bv\\b|\\bvs\\b)/i.test(t));\n" +
      "    if (cand) name = cand;\n" +
      "  }\n" +
      "  if (!dateIso) { const t = document.querySelector('time[datetime]'); if (t) dateIso = t.getAttribute('datetime'); }\n" +
      "  return { name, dateIso };\n" +
      "}"
    );

    String name = null;
    String iso = null;
    if (meta != null) {
      Object n = meta.get("name");
      Object d = meta.get("dateIso");
      if (n instanceof String) name = (String) n;
      if (d instanceof String) iso = (String) d;
    }

    String when = iso;
    if (iso != null) {
      try {
        Instant inst = Instant.parse(iso);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.of("Europe/London"));
        when = fmt.format(inst);
      } catch (Exception ignored) {}
    }

    if (name != null && when != null) {
      System.out.println(name + " — " + when);
    } else if (name != null) {
      System.out.println(name);
    } else if (when != null) {
      System.out.println(when);
    }
  }

  private static void scrapeCorrectScoreOdds(Page page) {
    // Open All Markets (if present)
    String[] allMarketsSelectors = new String[] {
      "button:has-text('All Markets')",
      "a:has-text('All Markets')",
      "[role=button]:has-text('All Markets')",
      "text=All Markets"
    };
    for (String sel : allMarketsSelectors) {
      try {
        Locator loc = page.locator(sel);
        if (loc.count() > 0) { loc.first().click(new Locator.ClickOptions().setForce(true).setTimeout(6000)); break; }
      } catch (Throwable ignored) {}
    }

    // Open Correct Score
    String[] csSelectors = new String[] {
      "button:has-text('Correct Score')",
      "a:has-text('Correct Score')",
      "[role=tab]:has-text('Correct Score')",
      "[role=button]:has-text('Correct Score')",
      "text=Correct Score"
    };
    boolean csOpen = false;
    for (String sel : csSelectors) {
      try {
        Locator cs = page.locator(sel);
        if (cs.count() > 0) { cs.first().scrollIntoViewIfNeeded(); cs.first().click(new Locator.ClickOptions().setForce(true).setTimeout(6000)); csOpen = true; break; }
      } catch (Throwable ignored) {}
    }
    if (!csOpen) {
      try {
        Boolean ok = (Boolean) page.evaluate(
          "() => {\n" +
          "  const el = [...document.querySelectorAll('*')].find(e => /\\bCorrect Score\\b/i.test((e.textContent||'').trim()));\n" +
          "  if (!el) return false;\n" +
          "  let t = el; while (t && !(t instanceof HTMLButtonElement || t instanceof HTMLAnchorElement)) t = t.parentElement;\n" +
          "  (t||el).dispatchEvent(new MouseEvent('click',{bubbles:true}));\n" +
          "  return true;\n" +
          "}"
        );
        csOpen = Boolean.TRUE.equals(ok);
      } catch (Throwable ignored) {}
    }

  page.waitForTimeout(1200);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> odds = (List<Map<String, Object>>) page.evaluate(
      "() => {\n" +
      "  const getText = el => (el && el.textContent ? el.textContent.replace(/\\s+/g,' ').trim() : '');\n" +
      "  const isVisible = el => { if (!el) return false; const s = getComputedStyle(el); if (s.display==='none'||s.visibility==='hidden'||parseFloat(s.opacity)===0) return false; const r = el.getBoundingClientRect(); return r.width>0 && r.height>0; };\n" +
      "  const fracRe = /\\b\\d{1,3}\\/\\d{1,3}\\b/;\n" +
      "  const scoreRe = /\\b(\\d)\\s*-\\s*(\\d)\\b/;\n" +
      "\n" +
      "  // Prefer the specific Correct Score card by class suffix; fallback to a heading search\n" +
      "  let card = document.querySelector(\"div[class*='-correctScoreCard']\");\n" +
      "  if (!card) {\n" +
      "    const heading = [...document.querySelectorAll('*')].find(e => /\\bCorrect Score\\b/i.test(getText(e)));\n" +
      "    if (heading) {\n" +
      "      // try nearby card container\n" +
      "      card = heading.closest('section,div,article')?.querySelector(\"div[class*='-correctScoreCard']\") || heading.parentElement?.querySelector(\"div[class*='-correctScoreCard']\");\n" +
      "    }\n" +
      "  }\n" +
      "  if (!card) return [];\n" +
      "\n" +
      "  const showMore = card.querySelector(\"button[class*='-showMoreButton']\");\n" +
      "  const cutY = showMore ? showMore.getBoundingClientRect().top : Number.POSITIVE_INFINITY;\n" +
      "\n" +
      "  const columns = [...card.querySelectorAll(\"div[class*='-correctScoreRunnerColumn']\")];\n" +
      "  const out = [];\n" +
      "  for (const col of columns) {\n" +
      "    const lines = [...col.querySelectorAll(\"div[class*='-correctScoreRunnerLine']\")];\n" +
      "    for (const line of lines) {\n" +
      "      if (!isVisible(line)) continue;\n" +
      "      const r = line.getBoundingClientRect();\n" +
      "      if (r.top >= cutY) continue;\n" +
      "      const scoreEl = line.querySelector(\"*[class*='-runnerName']\");\n" +
      "      const scoreTxt = getText(scoreEl);\n" +
      "      if (!scoreRe.test(scoreTxt)) continue;\n" +
      "      // Find the price label within the button and normalize it (strip leading zeros)\n" +
      "      const priceLabel = line.querySelector(\"button span[class*='-labelTwoLines'], button [class*='-labelContainer'], button [class*='-label']\");\n" +
      "      const normalizeFrac = (s) => { const m = (s||'').match(fracRe); if (!m) return null; const [a,b] = m[0].split('/'); const A = String(parseInt(a,10)); const B = String(parseInt(b,10)); if (!A || !B || A==='NaN' || B==='NaN') return null; return A+ '/' + B; };\n" +
      "      let price = normalizeFrac(getText(priceLabel));\n" +
      "      if (!price) price = normalizeFrac(getText(line));\n" +
      "      if (!price) continue;\n" +
      "      if (!price) continue;\n" +
      "      out.push({ score: scoreTxt.replace(/\\s+/g,''), odds: price });\n" +
      "      if (out.length === 12) return out;\n" +
      "    }\n" +
      "  }\n" +
      "  return out.slice(0,12);\n" +
      "}"
    );

    if (odds == null || odds.isEmpty()) {
      System.out.println("No 'Correct Score' odds found.");
      return;
    }

    // Group by normalized odds value (e.g., 9/1 -> 9, 13/2 -> 6.5), rounding to 2 dp and trimming trailing zeros
    Map<String, List<String>> grouped = new TreeMap<>((a,b) -> Double.compare(Double.parseDouble(a), Double.parseDouble(b)));
    for (Map<String, Object> row : odds) {
      String frac = String.valueOf(row.get("odds"));
      String score = String.valueOf(row.get("score"));
      String[] parts = frac.split("/");
      if (parts.length != 2) continue;
      try {
        BigDecimal num = new BigDecimal(parts[0]);
        BigDecimal den = new BigDecimal(parts[1]);
        if (den.compareTo(BigDecimal.ZERO) == 0) continue;
        BigDecimal dec = num.divide(den, 2, RoundingMode.HALF_UP).stripTrailingZeros();
        String key = dec.toPlainString();
        grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(score);
      } catch (NumberFormatException ignored) {}
    }

    if (!grouped.isEmpty()) {
      for (Map.Entry<String, List<String>> e : grouped.entrySet()) {
        System.out.println(e.getKey() + ": " + String.join(", ", e.getValue()));
      }
    }
  }
}
