package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;

class FeatureFlagDevtoolsMappingTest {
  private final Path webapp = Path.of("src/main/webapp");

  @ParameterizedTest
  @CsvSource({
    "/inventory,/inventory,/inventory/start.jsp",
    "/inventory/search,/inventory/**,/inventory/start.jsp",
    "/gallery,/gallery,/eln/gallery.jsp",
    "/gallery/item/123,^/gallery/item/([0-9]+)$,/eln/gallery.jsp",
    "/gallery/item/123/2,^/gallery/item/([0-9]+)/([0-9]+)$,/eln/gallery.jsp",
    "/gallery/123,^/gallery/([0-9]+)$,/eln/gallery.jsp",
    "/listOfMaterials/,/listOfMaterials/**,/inventory/start.jsp",
    "/listOfMaterials/123,/listOfMaterials/**,/inventory/start.jsp"
  })
  void rewrittenEntryPointsReachJspWithCapability(String url, String from, String jsp)
      throws Exception {
    Document rewrites = read("WEB-INF/urlrewrite.xml");
    String target = xpath(rewrites, "string(//rule[from='" + from + "']/to)");
    assertEquals(jsp, target, url);
    String regex = from.startsWith("^") ? from : from.replace("/**", "/.*");
    assertTrue(url.matches(regex));
    assertTrue(
        Files.readString(webapp.resolve(jsp.substring(1)))
            .contains("data-can-use-devtools=\"${canUseDevtools eq true}\""));
    Document mappings = read("WEB-INF/web.xml");
    String mapping = "//filter-mapping[filter-name='featureFlagDevtoolsFilter']";
    assertEquals(
        "true", xpath(mappings, "string(boolean(" + mapping + "[url-pattern='" + jsp + "']))"));
    assertEquals(
        "true", xpath(mappings, "string(boolean(" + mapping + "/dispatcher[text()='FORWARD']))"));
  }

  @Test
  void directJspRequestsRunAfterShiro() throws Exception {
    Document mappings = read("WEB-INF/web.xml");
    String mapping = "//filter-mapping[filter-name='featureFlagDevtoolsFilter']";
    assertEquals(
        "true",
        xpath(
            mappings,
            "string(boolean("
                + mapping
                + "/preceding-sibling::filter-mapping[filter-name='shiroFilter']))"));
    assertEquals(
        "true", xpath(mappings, "string(boolean(" + mapping + "/dispatcher[text()='REQUEST']))"));
  }

  private Document read(String file) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    return factory.newDocumentBuilder().parse(webapp.resolve(file).toFile());
  }

  private String xpath(Document document, String expression) throws Exception {
    return XPathFactory.newInstance().newXPath().evaluate(expression, document);
  }
}
