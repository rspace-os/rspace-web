package com.researchspace.linkedelements;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

interface ElementSelector {
  Elements select(Element rootElement);
}
