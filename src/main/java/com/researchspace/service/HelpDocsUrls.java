package com.researchspace.service;

public final class HelpDocsUrls {

  private static final String ARTICLE_BASE = "https://documentation.researchspace.com/article";

  private HelpDocsUrls() {}

  public static String urlFromSlug(String slug) {
    return ARTICLE_BASE + "/" + slug;
  }
}
