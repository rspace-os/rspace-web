package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class ApiV2StatelessRequestFilterTest {

  @Test
  void removesCookiesAndSessionAccessBeforeContinuingTheChain() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/users/me");
    request.setCookies(new Cookie("JSESSIONID", "browser-session"), new Cookie("theme", "dark"));
    request.addHeader("Cookie", "JSESSIONID=browser-session; theme=dark");
    request.addHeader("X-Request-Id", "request-id");
    request.setRequestedSessionId("browser-session");
    request.getSession(true).setAttribute("user", "browser-user");
    MockFilterChain chain = new MockFilterChain();

    new ApiV2StatelessRequestFilter().doFilter(request, new MockHttpServletResponse(), chain);

    HttpServletRequest filtered = (HttpServletRequest) chain.getRequest();
    assertNull(filtered.getCookies());
    assertNull(filtered.getHeader("Cookie"));
    assertFalse(filtered.getHeaders("Cookie").hasMoreElements());
    assertFalse(Collections.list(filtered.getHeaderNames()).contains("Cookie"));
    assertEquals("request-id", filtered.getHeader("X-Request-Id"));
    assertNull(filtered.getSession(false));
    assertNull(filtered.getRequestedSessionId());
    assertFalse(filtered.isRequestedSessionIdFromCookie());
    assertThrows(IllegalStateException.class, filtered::getSession);
  }

  @Test
  void preservesTheExistingSessionOnlyForTokenCreation() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/oauth/tokens");
    request.getSession(true).setAttribute("user", "browser-user");
    MockFilterChain chain = new MockFilterChain();

    new ApiV2StatelessRequestFilter().doFilter(request, new MockHttpServletResponse(), chain);

    HttpServletRequest filtered = (HttpServletRequest) chain.getRequest();
    assertEquals("browser-user", filtered.getSession(false).getAttribute("user"));
  }

  @Test
  void stripsTheSessionFromOtherMethodsOnTheTokenPath() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/oauth/tokens");
    request.getSession(true).setAttribute("user", "browser-user");
    MockFilterChain chain = new MockFilterChain();

    new ApiV2StatelessRequestFilter().doFilter(request, new MockHttpServletResponse(), chain);

    HttpServletRequest filtered = (HttpServletRequest) chain.getRequest();
    assertNull(filtered.getSession(false));
  }

  @Test
  void isMappedBeforeShiroForEveryServletDispatchType() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    Document webXml =
        factory.newDocumentBuilder().parse(Path.of("src/main/webapp/WEB-INF/web.xml").toFile());

    Element statelessMapping = filterMapping(webXml, "apiV2StatelessRequestFilter");
    Element shiroMapping = filterMapping(webXml, "shiroFilter");

    assertTrue(
        precedes(statelessMapping, shiroMapping),
        "The REST API v2 cookie boundary must run before Shiro");
    assertEquals(
        "/api/v2/*",
        statelessMapping.getElementsByTagName("url-pattern").item(0).getTextContent().trim());
    assertEquals(
        Set.of("REQUEST", "FORWARD", "INCLUDE", "ERROR", "ASYNC"),
        childText(statelessMapping, "dispatcher"));
  }

  private static Element filterMapping(Document webXml, String filterName) {
    NodeList mappings = webXml.getElementsByTagName("filter-mapping");
    for (int index = 0; index < mappings.getLength(); index++) {
      Element mapping = (Element) mappings.item(index);
      String mappedName =
          mapping.getElementsByTagName("filter-name").item(0).getTextContent().trim();
      if (filterName.equals(mappedName)) {
        return mapping;
      }
    }
    throw new AssertionError("No filter mapping for " + filterName);
  }

  private static boolean precedes(Element first, Element second) {
    for (var node = first.getNextSibling(); node != null; node = node.getNextSibling()) {
      if (node == second) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> childText(Element parent, String tagName) {
    Set<String> values = new HashSet<>();
    NodeList children = parent.getElementsByTagName(tagName);
    for (int index = 0; index < children.getLength(); index++) {
      values.add(children.item(index).getTextContent().trim());
    }
    return values;
  }
}
