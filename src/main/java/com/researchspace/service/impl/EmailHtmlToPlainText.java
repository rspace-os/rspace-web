package com.researchspace.service.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * Derives the plain-text alternative of an HTML email from its rendered HTML, so templates only
 * need authoring once. Hyperlinks keep their target visible as "text (url)"; block-level elements
 * become paragraph breaks.
 */
public final class EmailHtmlToPlainText {

  private EmailHtmlToPlainText() {}

  public static String toPlainText(String html) {
    Formatter formatter = new Formatter();
    NodeTraversor.traverse(formatter, Jsoup.parse(html).body());
    return formatter.toString();
  }

  private static class Formatter implements NodeVisitor {
    private final StringBuilder out = new StringBuilder();
    private boolean spacePending;

    @Override
    public void head(Node node, int depth) {
      if (node instanceof TextNode textNode) {
        appendText(textNode.text());
      } else if (node instanceof Element element) {
        if ("li".equals(element.normalName())) {
          newlines(1);
          Element parent = element.parent();
          out.append(
              parent != null && "ol".equals(parent.normalName())
                  ? (element.elementSiblingIndex() + 1) + ". "
                  : "- ");
        } else if ("br".equals(element.normalName()) || element.isBlock()) {
          newlines(1);
        }
      }
    }

    @Override
    public void tail(Node node, int depth) {
      if (!(node instanceof Element el)) {
        return;
      }
      if ("a".equals(el.normalName())) {
        String absoluteHref = el.absUrl("href");
        String href = absoluteHref.isEmpty() ? el.attr("href") : absoluteHref;
        if (!href.isEmpty() && !el.text().equals(href)) {
          appendText(" (" + href + ")");
        }
      } else if ("li".equals(el.normalName())) {
        newlines(1);
      } else if (el.isBlock()) {
        newlines(2);
      }
    }

    private void appendText(String text) {
      String normalised = text.replaceAll("\\s+", " ");
      if (normalised.isBlank()) {
        spacePending = out.length() > 0 && out.charAt(out.length() - 1) != '\n';
        return;
      }
      boolean startsWithSpace = normalised.charAt(0) == ' ';
      boolean endsWithSpace = normalised.charAt(normalised.length() - 1) == ' ';
      normalised = normalised.strip();
      if ((spacePending || startsWithSpace)
          && out.length() > 0
          && out.charAt(out.length() - 1) != '\n') {
        out.append(' ');
      }
      out.append(normalised);
      spacePending = endsWithSpace;
    }

    /** Pads the output with newlines up to the requested paragraph gap, never beyond it. */
    private void newlines(int wanted) {
      spacePending = false;
      if (out.length() == 0) {
        return;
      }
      int trailing = 0;
      while (trailing < out.length() && out.charAt(out.length() - 1 - trailing) == '\n') {
        trailing++;
      }
      if (trailing == out.length()) {
        return;
      }
      out.append("\n".repeat(Math.max(0, wanted - trailing)));
    }

    @Override
    public String toString() {
      return out.toString().strip();
    }
  }
}
