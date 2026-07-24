package com.researchspace.service;

/**
 * Builds RSpace help documentation URLs from an article slug. The slug for a given article is
 * looked up via the {@code common:help.<docLink>} keys shared with the frontend catalog, so a
 * single entry stays in sync for both.
 */
public final class HelpDocsUrls {

  private static final String ARTICLE_BASE = "https://documentation.researchspace.com/article";

  private HelpDocsUrls() {}

  public static String urlFromSlug(String slug) {
    return ARTICLE_BASE + "/" + slug;
  }
}
