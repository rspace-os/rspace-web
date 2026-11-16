package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Runs the real SiteMesh filter and {@code sitemesh3.xml} the way the container does: a REQUEST
 * pass on the incoming URL, then the rewrite filter's FORWARD to {@code /app/...}, or an ERROR
 * dispatch to a container error page. The filter only runs on the dispatcher types {@code web.xml}
 * maps it to.
 */
class SiteMeshDecorationTest {

  private static final Path WEBAPP = Path.of("src/main/webapp");
  private static final String BODY = "1.129.0";

  private MockServletContext servletContext;
  private ErrorPageSiteMeshFilter filter;
  private Set<DispatcherType> mappedDispatchers;

  @BeforeEach
  void setUp() throws Exception {
    servletContext = new MockServletContext(WEBAPP.toString(), new FileSystemResourceLoader());
    filter = new ErrorPageSiteMeshFilter();
    filter.init(new MockFilterConfig(servletContext, "sitemesh"));
    mappedDispatchers = sitemeshDispatchersFromWebXml();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/public/version", "/public/status", "/public/bannerImageName"})
  void excludedPlainTextEndpointsAreNotDecorated(String path) throws Exception {
    MockHttpServletResponse response = serve(path, path, null);

    assertNull(decoratorUsed(response));
    assertEquals(BODY, response.getContentAsString());
  }

  @Test
  void ajaxFragmentUnderMultipartMappedPrefixIsNotDecorated() throws Exception {
    MockHttpServletResponse response =
        serve("/workspace/ajax/search", "/workspace", "/ajax/search");

    assertNull(decoratorUsed(response));
    assertEquals(BODY, response.getContentAsString());
  }

  @Test
  void ordinaryPageIsDecorated() throws Exception {
    MockHttpServletResponse response = serve("/dashboard", "/dashboard", null);

    assertEquals("/WEB-INF/decorators/default.jsp", decoratorUsed(response));
  }

  @Test
  void publicPageIsDecoratedWithExternalPagesDecorator() throws Exception {
    MockHttpServletResponse response =
        serve("/public/maintenanceStatus", "/public/maintenanceStatus", null);

    assertEquals("/WEB-INF/decorators/externalPages.jsp", decoratorUsed(response));
  }

  /*
   * Tomcat's error dispatch carries forward attributes from the failed request; Jetty's carries
   * none. Either way the decision must follow the error page, as it did under SiteMesh 2.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void notFoundPageIsDecoratedEvenForAnExcludedAjaxUrl(boolean withForwardAttributes)
      throws Exception {
    MockHttpServletResponse response =
        serveErrorPage(404, "/404.jsp", "/workspace/ajax/search", withForwardAttributes);

    assertEquals("/WEB-INF/decorators/default.jsp", decoratorUsed(response));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void serverErrorPageIsNotDecorated(boolean withForwardAttributes) throws Exception {
    MockHttpServletResponse response =
        serveErrorPage(
            500, "/error.jsp", "/workspace/editor/structuredDocument/1", withForwardAttributes);

    assertNull(decoratorUsed(response));
    assertEquals(BODY, response.getContentAsString());
  }

  @Test
  void sitemeshIsMappedBeforeRewriteFilter() throws Exception {
    List<String> order = filterMappings().stream().map(SiteMeshDecorationTest::filterName).toList();

    assertTrue(
        order.indexOf("sitemesh") < order.indexOf("rewriteFilter"),
        "SiteMesh only sees the incoming request if it runs before the rewrite filter forwards it");
  }

  /**
   * Serves {@code path}, matched on the REQUEST pass to a servlet with the given servlet path and
   * path info, then forwarded by the rewrite filter to {@code /app + path}, where the controller
   * writes an HTML body.
   */
  private MockHttpServletResponse serve(String path, String servletPath, String pathInfo)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", path);
    request.setServletPath(servletPath);
    request.setPathInfo(pathInfo);
    MockHttpServletResponse response = new MockHttpServletResponse();

    FilterChain rewriteFilter =
        (req, res) ->
            dispatch(
                DispatcherType.FORWARD,
                forwardedToApp((HttpServletRequest) req, path),
                res,
                htmlController());

    dispatch(DispatcherType.REQUEST, request, response, rewriteFilter);
    return response;
  }

  /**
   * ERROR-dispatches the error page at {@code location} after {@code failedUri} failed. With
   * forward attributes, they are set the way Tomcat sets them for a URL under the multipart-mapped
   * {@code /workspace/*} servlet.
   */
  private MockHttpServletResponse serveErrorPage(
      int status, String location, String failedUri, boolean withForwardAttributes)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", location);
    request.setServletPath(location);
    request.setDispatcherType(DispatcherType.ERROR);
    request.setAttribute("jakarta.servlet.error.request_uri", failedUri);
    if (withForwardAttributes) {
      request.setAttribute("jakarta.servlet.forward.request_uri", failedUri);
      request.setAttribute("jakarta.servlet.forward.servlet_path", "/workspace");
      request.setAttribute(
          "jakarta.servlet.forward.path_info", failedUri.substring("/workspace".length()));
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(status);

    dispatch(DispatcherType.ERROR, request, response, htmlController());
    return response;
  }

  private static FilterChain htmlController() {
    return (req, res) -> {
      res.setContentType("text/html;charset=UTF-8");
      res.getWriter().write(BODY);
    };
  }

  private void dispatch(
      DispatcherType type, ServletRequest request, ServletResponse response, FilterChain next)
      throws IOException, ServletException {
    if (mappedDispatchers.contains(type)) {
      filter.doFilter(request, response, next);
    } else {
      next.doFilter(request, response);
    }
  }

  private static HttpServletRequest forwardedToApp(HttpServletRequest original, String path) {
    String originalServletPath = original.getServletPath();
    String originalPathInfo = original.getPathInfo();
    return new HttpServletRequestWrapper(original) {
      @Override
      public DispatcherType getDispatcherType() {
        return DispatcherType.FORWARD;
      }

      @Override
      public String getRequestURI() {
        return "/app" + path;
      }

      @Override
      public String getServletPath() {
        return "/app";
      }

      @Override
      public String getPathInfo() {
        return path;
      }

      @Override
      public Object getAttribute(String name) {
        return switch (name) {
          case "jakarta.servlet.forward.request_uri" -> path;
          case "jakarta.servlet.forward.servlet_path" -> originalServletPath;
          case "jakarta.servlet.forward.path_info" -> originalPathInfo;
          default -> super.getAttribute(name);
        };
      }
    };
  }

  private static String decoratorUsed(MockHttpServletResponse response) {
    return response.getForwardedUrl() != null
        ? response.getForwardedUrl()
        : response.getIncludedUrl();
  }

  private static Set<DispatcherType> sitemeshDispatchersFromWebXml() throws Exception {
    for (Element mapping : filterMappings()) {
      if ("sitemesh".equals(filterName(mapping))) {
        Set<DispatcherType> types = EnumSet.noneOf(DispatcherType.class);
        NodeList dispatchers = mapping.getElementsByTagName("dispatcher");
        for (int j = 0; j < dispatchers.getLength(); j++) {
          types.add(DispatcherType.valueOf(dispatchers.item(j).getTextContent().trim()));
        }
        return types.isEmpty() ? EnumSet.of(DispatcherType.REQUEST) : types;
      }
    }
    throw new IllegalStateException("No sitemesh filter-mapping in web.xml");
  }

  private static List<Element> filterMappings() throws Exception {
    Document webXml;
    try (InputStream in = Files.newInputStream(WEBAPP.resolve("WEB-INF/web.xml"))) {
      webXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
    }
    NodeList mappings = webXml.getElementsByTagName("filter-mapping");
    List<Element> elements = new ArrayList<>();
    for (int i = 0; i < mappings.getLength(); i++) {
      elements.add((Element) mappings.item(i));
    }
    return elements;
  }

  private static String filterName(Element mapping) {
    return mapping.getElementsByTagName("filter-name").item(0).getTextContent().trim();
  }
}
