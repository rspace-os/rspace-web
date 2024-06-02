package com.researchspace.linkedelements;

import com.researchspace.core.util.FieldParserConstants;
import java.util.Iterator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Gets an iterator over and img attachment links/images in a text field. This does not necessarily
 * iterate in the same order as is found in the content.
 */
class FieldElementIterator implements Iterator<Element> {

  private Iterator<Element> internalIterator;
  private Elements elements;

  public FieldElementIterator(String content) {
    super();
    Document d = Jsoup.parse(content);
    elements = d.getElementsByTag(FieldParserConstants.TAG_IMG);
    elements.addAll(d.getElementsByTag(FieldParserConstants.TAG_LINK));
    elements.addAll(d.getElementsByTag("div"));
    elements = elements.select("[class]");
    internalIterator = elements.iterator();
  }

  @Override
  public boolean hasNext() {
    return internalIterator.hasNext();
  }

  @Override
  public Element next() {
    return internalIterator.next();
  }

  @Override
  public void remove() {
    internalIterator.remove();
  }
}
