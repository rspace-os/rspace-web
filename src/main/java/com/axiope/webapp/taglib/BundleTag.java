package com.axiope.webapp.taglib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

@Setter
public class BundleTag extends TagSupport {

  private static final Logger log = LoggerFactory.getLogger(BundleTag.class);
  private static final long serialVersionUID = 821688607387514092L;
  static final String VITE_MANIFEST_PATH = "/ui/dist/.vite/manifest.json";
  static final String VITE_ENTRYPOINTS_PATH = "/ui/bundleEntries.json";
  static final String DIST_PUBLIC_PATH = "/ui/dist/";
  static final String VITE_CLIENT_PATH = "@vite/client";
  static final String REACT_REFRESH_PATH = "@react-refresh";
  public static final String REACT_DEV_MODE_PROPERTY = "reactDevMode";
  static final String MANIFEST_CACHE_ATTR = BundleTag.class.getName() + ".MANIFEST_CACHE";
  static final String ENTRYPOINTS_CACHE_ATTR = BundleTag.class.getName() + ".ENTRYPOINTS_CACHE";
  static final String REQUEST_MANIFEST_CACHE_ATTR =
      BundleTag.class.getName() + ".REQUEST_MANIFEST_CACHE";
  static final String DEV_MODE_CACHE_ATTR = BundleTag.class.getName() + ".DEV_MODE";
  static final String RENDERED_ASSETS_ATTR = BundleTag.class.getName() + ".RENDERED_ASSETS";
  static final String REACT_PREAMBLE_DEDUPE_KEY = "script:module:inline:react-refresh-preamble";

  /**
   * Servlet context attribute that holds the cache-busting version token. Set at startup by {@link
   * com.axiope.webapp.listener.StartupListener}: a random UUID in dev mode, or the RSpace version
   * string in production. Appended as {@code ?v=<token>} to all local asset URLs.
   */
  public static final String CACHE_VERSION_ATTR = BundleTag.class.getName() + ".CACHE_VERSION";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private String bundle;

  @Override
  public int doStartTag() throws JspException {
    if (StringUtils.isBlank(bundle)) {
      throw new IllegalStateException("bundle attribute must be provided");
    }

    renderBundle();

    return TagSupport.SKIP_BODY;
  }

  void renderBundle() throws JspException {
    if (isHmrEnabled()) {
      renderHmrBundle();
      return;
    }

    ChunkManifest manifest = getManifestCache();
    ChunkManifest.BundleAssets assets = manifest.getBundleAssets(bundle);
    if (assets == null) {
      assets = refreshManifestCache().getBundleAssets(bundle);
    }

    if (assets == null) {
      throw new JspException("No bundle manifest entry found for bundle: " + bundle);
    }

    if (assets.getScripts().isEmpty()) {
      throw new JspException("Bundle manifest entry contains no scripts for bundle: " + bundle);
    }

    for (String styleUrl : assets.getStyles()) {
      renderLinkTag("stylesheet", withCacheVersion(styleUrl));
    }

    for (String preloadUrl : assets.getPreloads()) {
      renderLinkTag("modulepreload", withCacheVersion(preloadUrl));
    }

    for (String scriptUrl : assets.getScripts()) {
      renderModuleScriptTag(withCacheVersion(scriptUrl));
    }
  }

  void renderHmrBundle() throws JspException {
    String entryPath = getEntrypoints().get(bundle);
    if (StringUtils.isBlank(entryPath)) {
      throw new JspException("No Vite entrypoint found for bundle: " + bundle);
    }

    renderModuleScriptTag(toDevServerUrl(VITE_CLIENT_PATH));
    renderModuleScriptTag(toDevServerUrl(entryPath));
  }

  boolean isHmrEnabled() {
    return isReactDevMode();
  }

  /**
   * Builds a same-origin asset URL under {@link #DIST_PUBLIC_PATH}. In development the request is
   * transparently reverse-proxied by {@code ViteDevServerProxyServlet} to the local Vite dev
   * server, so JSPs emit identical URLs in dev and production.
   */
  String toDevServerUrl(String relativePath) {
    return toDevServerUrlStatic(relativePath);
  }

  @SuppressWarnings("unchecked")
  Map<String, String> getEntrypoints() {
    ServletContext servletContext = pageContext.getServletContext();
    Object existing = servletContext.getAttribute(ENTRYPOINTS_CACHE_ATTR);
    if (existing instanceof Map) {
      return (Map<String, String>) existing;
    }

    Map<String, String> loaded = loadEntrypoints(servletContext);
    servletContext.setAttribute(ENTRYPOINTS_CACHE_ATTR, loaded);
    return loaded;
  }

  static Map<String, String> loadEntrypoints(ServletContext servletContext) {
    try (InputStream inputStream = servletContext.getResourceAsStream(VITE_ENTRYPOINTS_PATH)) {
      if (inputStream == null) {
        return Collections.emptyMap();
      }

      JsonNode root = objectMapper.readTree(inputStream);
      Map<String, String> entrypoints = new LinkedHashMap<>();
      root.fieldNames()
          .forEachRemaining(
              bundleName -> {
                JsonNode entryNode = root.path(bundleName);
                if (entryNode.isTextual()) {
                  entrypoints.put(bundleName, entryNode.asText());
                }
              });
      return entrypoints;
    } catch (IOException e) {
      log.warn("Failed to read Vite entrypoints from {}", VITE_ENTRYPOINTS_PATH, e);
      return Collections.emptyMap();
    }
  }

  @SuppressWarnings("unchecked")
  Set<String> getRenderedAssetKeys() {
    Object existing = getRequest().getAttribute(RENDERED_ASSETS_ATTR);
    if (existing instanceof Set) {
      return (Set<String>) existing;
    }

    Set<String> renderedAssets = new LinkedHashSet<>();
    getRequest().setAttribute(RENDERED_ASSETS_ATTR, renderedAssets);
    return renderedAssets;
  }

  void renderLinkTag(String rel, String href) throws JspException {
    String dedupeKey = "link:" + rel + ":" + href;
    if (!getRenderedAssetKeys().add(dedupeKey)) {
      return;
    }

    writeTag("<link rel=\"" + escapeHtml(rel) + "\" href=\"" + escapeHtml(href) + "\" />");
  }

  void renderModuleScriptTag(String src) throws JspException {
    String dedupeKey = "script:module:" + src;
    if (!getRenderedAssetKeys().add(dedupeKey)) {
      return;
    }

    writeTag("<script type=\"module\" src=\"" + escapeHtml(src) + "\"></script>");
  }

  void writeTag(String html) throws JspException {
    try {
      getWriter().write(html);
    } catch (IOException e) {
      throw new JspException("Failed to render bundle tags", e);
    }
  }

  /**
   * Appends the cache-busting version token as a {@code ?v=<token>} query parameter to local asset
   * URLs (those under {@link #DIST_PUBLIC_PATH}). External and HMR URLs are returned unchanged.
   * Returns the URL unchanged when no version token has been stored.
   */
  String withCacheVersion(String url) {
    if (StringUtils.isBlank(url) || !url.startsWith(DIST_PUBLIC_PATH)) {
      return url;
    }
    String version = getCacheVersion();
    if (StringUtils.isBlank(version)) {
      return url;
    }
    return url + "?v=" + version;
  }

  /**
   * Returns the cache-busting version token. In dev mode a fresh UUID is generated and stored as a
   * request attribute so that every page hit gets a unique token (ensuring browsers always re-fetch
   * assets after a rebuild), while multiple tag invocations within the same request share the same
   * token. In production the application-version string stored in the servlet context at startup is
   * returned.
   */
  String getCacheVersion() {
    if (isDevMode()) {
      String requestAttr = CACHE_VERSION_ATTR + ".REQUEST";
      Object existing = getRequest().getAttribute(requestAttr);
      if (existing instanceof String) {
        return (String) existing;
      }
      String uuid = UUID.randomUUID().toString();
      getRequest().setAttribute(requestAttr, uuid);
      return uuid;
    }
    Object value = pageContext.getServletContext().getAttribute(CACHE_VERSION_ATTR);
    return value instanceof String ? (String) value : null;
  }

  public static void preWarmManifestCache(ServletContext servletContext, boolean isDevMode) {
    servletContext.setAttribute(DEV_MODE_CACHE_ATTR, isDevMode);

    if (isDevMode) {
      servletContext.removeAttribute(MANIFEST_CACHE_ATTR);
      return;
    }

    ChunkManifest manifest = ChunkManifest.load(servletContext);
    if (manifest.isEmpty()) {
      servletContext.removeAttribute(MANIFEST_CACHE_ATTR);
    } else {
      servletContext.setAttribute(MANIFEST_CACHE_ATTR, manifest);
    }
  }

  /**
   * Returns the Vite manifest, using a three-tier caching strategy:
   *
   * <ul>
   *   <li><b>Production:</b> manifest is pre-warmed once at startup and cached in the {@link
   *       ServletContext} for the lifetime of the application.
   *   <li><b>Dev ({@code run} profile, no HMR):</b> manifest is cached per-request so that a
   *       rebuild between page loads is picked up without a Jetty restart.
   *   <li><b>HMR ({@code reactDevMode=true}):</b> manifest is not used at all — {@link
   *       #renderHmrBundle()} serves modules directly from the Vite dev server.
   * </ul>
   */
  ChunkManifest getManifestCache() {
    if (isDevMode()) {
      Object existingRequestManifest = getRequest().getAttribute(REQUEST_MANIFEST_CACHE_ATTR);
      if (existingRequestManifest instanceof ChunkManifest) {
        return (ChunkManifest) existingRequestManifest;
      }

      ChunkManifest refreshedManifest = refreshManifestCache();
      if (!refreshedManifest.isEmpty()) {
        getRequest().setAttribute(REQUEST_MANIFEST_CACHE_ATTR, refreshedManifest);
      }
      return refreshedManifest;
    }

    ServletContext servletContext = pageContext.getServletContext();
    Object existing = servletContext.getAttribute(MANIFEST_CACHE_ATTR);
    if (existing instanceof ChunkManifest) {
      ChunkManifest manifest = (ChunkManifest) existing;
      if (!manifest.isEmpty()) {
        return manifest;
      }
    }

    return refreshManifestCache();
  }

  ChunkManifest refreshManifestCache() {
    ServletContext servletContext = pageContext.getServletContext();
    ChunkManifest manifest = ChunkManifest.load(servletContext);
    getRequest().removeAttribute(REQUEST_MANIFEST_CACHE_ATTR);
    if (manifest.isEmpty()) {
      servletContext.removeAttribute(MANIFEST_CACHE_ATTR);
    } else {
      servletContext.setAttribute(MANIFEST_CACHE_ATTR, manifest);
    }
    return manifest;
  }

  boolean isDevMode() {
    ServletContext servletContext = pageContext.getServletContext();
    Object cachedValue = servletContext.getAttribute(DEV_MODE_CACHE_ATTR);
    if (cachedValue instanceof Boolean) {
      return (Boolean) cachedValue;
    }

    boolean isReactDevMode = isReactDevMode();
    if (isReactDevMode) {
      servletContext.setAttribute(DEV_MODE_CACHE_ATTR, Boolean.TRUE);
      return true;
    }

    WebApplicationContext applicationContext =
        WebApplicationContextUtils.getWebApplicationContext(servletContext);
    if (applicationContext == null) {
      return false;
    }

    Environment environment = applicationContext.getEnvironment();
    boolean isDevMode = environment.acceptsProfiles(Profiles.of("run"));

    servletContext.setAttribute(DEV_MODE_CACHE_ATTR, isDevMode);
    return isDevMode;
  }

  boolean isReactDevMode() {
    ServletContext servletContext = pageContext.getServletContext();
    WebApplicationContext applicationContext =
        WebApplicationContextUtils.getWebApplicationContext(servletContext);
    if (applicationContext != null) {
      return isTrue(applicationContext.getEnvironment().getProperty(REACT_DEV_MODE_PROPERTY));
    }

    return Boolean.getBoolean(REACT_DEV_MODE_PROPERTY);
  }

  HttpServletRequest getRequest() {
    return (HttpServletRequest) pageContext.getRequest();
  }

  JspWriter getWriter() {
    return pageContext.getOut();
  }

  static String escapeHtml(String value) {
    return StringEscapeUtils.escapeHtml4(value);
  }

  static String reactRefreshPreambleHtml() {
    return "<script type=\"module\">"
        + "import RefreshRuntime from \""
        + toDevServerUrlStatic(REACT_REFRESH_PATH)
        + "\";"
        + "RefreshRuntime.injectIntoGlobalHook(window);"
        + "window.$RefreshReg$ = () => {};"
        + "window.$RefreshSig$ = () => (type) => type;"
        + "window.__vite_plugin_react_preamble_installed__ = true;"
        + "</script>";
  }

  static String toDevServerUrlStatic(String relativePath) {
    String normalizedPath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
    return DIST_PUBLIC_PATH + normalizedPath;
  }

  static boolean isTrue(String value) {
    return Boolean.parseBoolean(StringUtils.trimToEmpty(value));
  }

  static final class ChunkManifest {
    private final Map<String, BundleAssets> bundles;

    private ChunkManifest(Map<String, BundleAssets> bundles) {
      this.bundles = bundles;
    }

    static ChunkManifest fromBundles(Map<String, BundleAssets> bundles) {
      return new ChunkManifest(bundles);
    }

    static ChunkManifest load(ServletContext servletContext) {
      try (InputStream inputStream = servletContext.getResourceAsStream(VITE_MANIFEST_PATH)) {
        if (inputStream == null) {
          return new ChunkManifest(Collections.emptyMap());
        }

        JsonNode root = objectMapper.readTree(inputStream);
        Map<String, ViteManifestEntry> manifestEntries = new LinkedHashMap<>();
        root.fieldNames()
            .forEachRemaining(
                manifestKey ->
                    manifestEntries.put(
                        manifestKey, ViteManifestEntry.fromJson(root.path(manifestKey))));

        Map<String, BundleAssets> parsedBundles = new LinkedHashMap<>();
        manifestEntries.forEach(
            (manifestKey, manifestEntry) -> {
              if (!manifestEntry.isEntry()) {
                return;
              }

              String bundleName = manifestEntry.getName();
              if (StringUtils.isBlank(bundleName)) {
                log.warn("Skipping Vite manifest entry without name: {}", manifestKey);
                return;
              }

              parsedBundles.put(
                  bundleName, toBundleAssets(bundleName, manifestEntries, manifestEntry));
            });
        return new ChunkManifest(parsedBundles);
      } catch (IOException e) {
        log.warn("Failed to read Vite manifest from {}", VITE_MANIFEST_PATH, e);
        return new ChunkManifest(Collections.emptyMap());
      }
    }

    private static BundleAssets toBundleAssets(
        String bundleName,
        Map<String, ViteManifestEntry> manifestEntries,
        ViteManifestEntry manifestEntry) {
      LinkedHashSet<String> styles = new LinkedHashSet<>();
      LinkedHashSet<String> preloads = new LinkedHashSet<>();
      collectStyles(styles, manifestEntry);
      collectPreloadsAndStyles(
          bundleName, manifestEntries, manifestEntry, preloads, styles, new LinkedHashSet<>());

      List<String> scripts =
          StringUtils.isBlank(manifestEntry.getFile())
              ? Collections.emptyList()
              : Collections.singletonList(toPublicAssetUrl(manifestEntry.getFile()));
      return new BundleAssets(new ArrayList<>(styles), new ArrayList<>(preloads), scripts);
    }

    private static void collectPreloadsAndStyles(
        String bundleName,
        Map<String, ViteManifestEntry> manifestEntries,
        ViteManifestEntry manifestEntry,
        Set<String> preloads,
        Set<String> styles,
        Set<String> visitedImportKeys) {
      for (String importKey : manifestEntry.getImports()) {
        if (!visitedImportKeys.add(importKey)) {
          continue;
        }

        ViteManifestEntry importedEntry = manifestEntries.get(importKey);
        if (importedEntry == null) {
          log.warn("Bundle {} references missing Vite manifest import: {}", bundleName, importKey);
          continue;
        }

        if (StringUtils.isNotBlank(importedEntry.getFile())) {
          preloads.add(toPublicAssetUrl(importedEntry.getFile()));
        }
        collectStyles(styles, importedEntry);
        collectPreloadsAndStyles(
            bundleName, manifestEntries, importedEntry, preloads, styles, visitedImportKeys);
      }
    }

    private static void collectStyles(Set<String> styles, ViteManifestEntry manifestEntry) {
      for (String cssPath : manifestEntry.getCss()) {
        styles.add(toPublicAssetUrl(cssPath));
      }
    }

    private static String toPublicAssetUrl(String assetPath) {
      if (StringUtils.isBlank(assetPath)) {
        return assetPath;
      }
      if (assetPath.startsWith("/")
          || assetPath.startsWith("http://")
          || assetPath.startsWith("https://")
          || assetPath.startsWith("//")) {
        return assetPath;
      }
      return toDevServerUrlStatic(assetPath);
    }

    BundleAssets getBundleAssets(String bundleName) {
      return bundles.get(bundleName);
    }

    boolean isEmpty() {
      return bundles.isEmpty();
    }

    private static List<String> readStringArray(JsonNode arrayNode) {
      if (!arrayNode.isArray()) {
        return Collections.emptyList();
      }

      List<String> values = new ArrayList<>();
      for (JsonNode item : arrayNode) {
        if (item.isTextual()) {
          values.add(item.asText());
        }
      }
      return values;
    }

    static final class ViteManifestEntry {
      private final String file;
      private final String name;
      private final boolean entry;
      private final List<String> imports;
      private final List<String> css;

      private ViteManifestEntry(
          String file, String name, boolean entry, List<String> imports, List<String> css) {
        this.file = file;
        this.name = name;
        this.entry = entry;
        this.imports = imports;
        this.css = css;
      }

      static ViteManifestEntry fromJson(JsonNode node) {
        return new ViteManifestEntry(
            node.path("file").asText(null),
            node.path("name").asText(null),
            node.path("isEntry").asBoolean(false),
            readStringArray(node.path("imports")),
            readStringArray(node.path("css")));
      }

      String getFile() {
        return file;
      }

      String getName() {
        return name;
      }

      boolean isEntry() {
        return entry;
      }

      List<String> getImports() {
        return imports;
      }

      List<String> getCss() {
        return css;
      }
    }

    static final class BundleAssets {
      private final List<String> styles;
      private final List<String> preloads;
      private final List<String> scripts;

      BundleAssets(List<String> styles, List<String> preloads, List<String> scripts) {
        this.styles = styles;
        this.preloads = preloads;
        this.scripts = scripts;
      }

      List<String> getStyles() {
        return styles;
      }

      List<String> getPreloads() {
        return preloads;
      }

      List<String> getScripts() {
        return scripts;
      }
    }
  }
}
