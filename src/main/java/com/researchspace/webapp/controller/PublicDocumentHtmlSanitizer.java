package com.researchspace.webapp.controller;

import java.util.ArrayList;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

class PublicDocumentHtmlSanitizer {

  String sanitize(String html) {
    String lowerHtml = StringUtils.lowerCase(html, Locale.ROOT);
    if (StringUtils.isBlank(html)
        || (!lowerHtml.contains("dbrepo_link")
            && !(lowerHtml.contains("data-tablesource") && lowerHtml.contains("dbrepo")))) {
      return html;
    }
    Document document = Jsoup.parseBodyFragment(html);
    document.outputSettings().prettyPrint(false);
    stripDBRepoLinks(document);
    stripDBRepoTableLinks(document);
    return document.body().html();
  }

  private void stripDBRepoLinks(Document document) {
    for (Element link : document.select("a.dbrepo_link")) {
      link.removeAttr("href");
      link.removeAttr("target");
      link.removeAttr("rel");
      link.removeClass("dbrepo_link");
      removeDBRepoDataAttributes(link);
    }
  }

  private void stripDBRepoTableLinks(Document document) {
    for (Element link :
        document.select(
            "table[data-tableSource=dbrepo] a[href], table[data-tablesource=dbrepo] a[href]")) {
      link.removeAttr("href");
      link.removeAttr("target");
      link.removeAttr("rel");
    }
  }

  private void removeDBRepoDataAttributes(Element element) {
    for (Attribute attribute : new ArrayList<>(element.attributes().asList())) {
      if (attribute.getKey().startsWith("data-dbrepo-")) {
        element.removeAttr(attribute.getKey());
      }
    }
  }
}
