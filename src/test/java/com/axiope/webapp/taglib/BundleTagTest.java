package com.axiope.webapp.taglib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class BundleTagTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock private HttpServletRequest request;
  @Mock private PageContext pageContext;
  @Mock private ServletContext servletContext;
  @Mock private JspWriter writer;

  private final StringBuilder output = new StringBuilder();
  private final Map<String, Object> requestAttributes = new LinkedHashMap<>();
  private final Map<String, Object> servletContextAttributes = new LinkedHashMap<>();
  private final Set<String> renderedAssets = new LinkedHashSet<>();
  private BundleTag.ChunkManifest manifest;
  private TestBundleTag tag;
  private String originalReactDevModeProperty;

  class TestBundleTag extends BundleTag {
    @Override
    Set<String> getRenderedAssetKeys() {
      return renderedAssets;
    }

    @Override
    ChunkManifest getManifestCache() {
      return manifest;
    }
  }

  @Before
  public void setUp() throws Exception {
    originalReactDevModeProperty = System.getProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
    System.clearProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
    manifest = BundleTag.ChunkManifest.fromBundles(new LinkedHashMap<>());
    tag = new TestBundleTag();
    tag.setPageContext(pageContext);
    output.setLength(0);
    requestAttributes.clear();
    servletContextAttributes.clear();
    renderedAssets.clear();

    lenient().when(pageContext.getRequest()).thenReturn(request);
    lenient().when(pageContext.getOut()).thenReturn(writer);
    lenient().when(pageContext.getServletContext()).thenReturn(servletContext);
    lenient()
        .when(servletContext.getAttribute(anyString()))
        .thenAnswer(
            invocation -> servletContextAttributes.get(invocation.getArgument(0, String.class)));
    lenient()
        .doAnswer(
            invocation -> {
              servletContextAttributes.put(
                  invocation.getArgument(0, String.class), invocation.getArgument(1));
              return null;
            })
        .when(servletContext)
        .setAttribute(anyString(), org.mockito.ArgumentMatchers.any());
    lenient()
        .doAnswer(
            invocation -> {
              servletContextAttributes.remove(invocation.getArgument(0, String.class));
              return null;
            })
        .when(servletContext)
        .removeAttribute(anyString());
    lenient()
        .when(request.getAttribute(anyString()))
        .thenAnswer(invocation -> requestAttributes.get(invocation.getArgument(0, String.class)));
    lenient()
        .doAnswer(
            invocation -> {
              requestAttributes.put(
                  invocation.getArgument(0, String.class), invocation.getArgument(1));
              return null;
            })
        .when(request)
        .setAttribute(anyString(), org.mockito.ArgumentMatchers.any());
    lenient()
        .doAnswer(
            invocation -> {
              requestAttributes.remove(invocation.getArgument(0, String.class));
              return null;
            })
        .when(request)
        .removeAttribute(anyString());

    lenient()
        .doAnswer(
            invocation -> {
              output.append(invocation.getArgument(0, String.class));
              return null;
            })
        .when(writer)
        .write(anyString());
  }

  @After
  public void tearDown() {
    if (originalReactDevModeProperty == null) {
      System.clearProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
    } else {
      System.setProperty(
          FrontendCacheVersion.REACT_DEV_MODE_PROPERTY, originalReactDevModeProperty);
    }
  }

  @Test
  public void rendersManifestBackedAssets() throws JspException {
    manifest =
        BundleTag.ChunkManifest.fromBundles(
            Collections.singletonMap(
                "appBar",
                new BundleTag.ChunkManifest.BundleAssets(
                    List.of("/ui/dist/assets/appBar.css"),
                    List.of("/ui/dist/chunks/shared.js"),
                    List.of("/ui/dist/appBar-abc123.js"))));

    tag.setBundle("appBar");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());
    assertTrue(
        output.toString().contains("rel=\"stylesheet\" href=\"/ui/dist/assets/appBar.css\""));
    assertTrue(
        output.toString().contains("rel=\"modulepreload\" href=\"/ui/dist/chunks/shared.js\""));
    assertTrue(output.toString().contains("type=\"module\" src=\"/ui/dist/appBar-abc123.js\""));
  }

  @Test
  public void renderingSameBundleTwiceDeduplicatesAssets() throws JspException {
    manifest =
        BundleTag.ChunkManifest.fromBundles(
            Collections.singletonMap(
                "appBar",
                new BundleTag.ChunkManifest.BundleAssets(
                    List.of("/ui/dist/assets/appBar.css"),
                    List.of("/ui/dist/chunks/shared.js"),
                    List.of("/ui/dist/appBar-abc123.js"))));

    tag.setBundle("appBar");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());
    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());
    assertEquals(3, renderedAssets.size());
  }

  @Test
  public void missingBundleThrowsJspException() {
    tag.setBundle("apps");

    JspException error = assertThrows(JspException.class, () -> tag.doStartTag());
    assertTrue(error.getMessage().contains("No bundle manifest entry found for bundle: apps"));
  }

  @Test
  public void loadsManifestFromDistPath() {
    when(servletContext.getResourceAsStream(BundleTag.VITE_MANIFEST_PATH))
        .thenReturn(manifestStream("appBar", "appBar-abc123.js"));

    BundleTag.ChunkManifest loadedManifest = BundleTag.ChunkManifest.load(servletContext);

    verify(servletContext).getResourceAsStream("/ui/dist/.vite/manifest.json");
    assertEquals(
        List.of("/ui/dist/assets/appBar.css", "/ui/dist/assets/shared.css"),
        loadedManifest.getBundleAssets("appBar").getStyles());
    assertEquals(
        List.of("/ui/dist/chunks/shared.js"),
        loadedManifest.getBundleAssets("appBar").getPreloads());
    assertEquals(
        List.of("/ui/dist/appBar-abc123.js"),
        loadedManifest.getBundleAssets("appBar").getScripts());
  }

  @Test
  public void loadsEntrypointsFromJsonPath() {
    when(servletContext.getResourceAsStream(BundleTag.VITE_ENTRYPOINTS_PATH))
        .thenReturn(entrypointsStream());

    Map<String, String> entrypoints = BundleTag.loadEntrypoints(servletContext);

    verify(servletContext).getResourceAsStream("/ui/bundleEntries.json");
    assertEquals("src/eln/AppBar.tsx", entrypoints.get("appBar"));
  }

  @Test
  public void getManifestCacheDoesNotCacheEmptyManifest() {
    BundleTag realTag = new BundleTag();
    realTag.setPageContext(pageContext);
    when(servletContext.getAttribute(BundleTag.MANIFEST_CACHE_ATTR)).thenReturn(null);
    when(servletContext.getResourceAsStream(BundleTag.VITE_MANIFEST_PATH)).thenReturn(null);

    BundleTag.ChunkManifest loadedManifest = realTag.getManifestCache();

    assertTrue(loadedManifest.isEmpty());
    verify(servletContext).removeAttribute(BundleTag.MANIFEST_CACHE_ATTR);
    verify(servletContext, never()).setAttribute(BundleTag.MANIFEST_CACHE_ATTR, loadedManifest);
  }

  @Test
  public void preWarmManifestCacheCachesParsedManifestForNonDevMode() {
    when(servletContext.getResourceAsStream(BundleTag.VITE_MANIFEST_PATH))
        .thenReturn(manifestStream("appBar", "appBar-abc123.js"));

    BundleTag.preWarmManifestCache(servletContext, false);

    assertEquals(
        Boolean.FALSE, servletContextAttributes.get(FrontendCacheVersion.DEV_MODE_CACHE_ATTR));
    Object cachedManifest = servletContextAttributes.get(BundleTag.MANIFEST_CACHE_ATTR);
    assertTrue(cachedManifest instanceof BundleTag.ChunkManifest);
    assertEquals(
        List.of("/ui/dist/appBar-abc123.js"),
        ((BundleTag.ChunkManifest) cachedManifest).getBundleAssets("appBar").getScripts());
  }

  @Test
  public void preWarmManifestCacheSkipsBundleCachingInDevMode() {
    BundleTag.preWarmManifestCache(servletContext, true);

    assertEquals(
        Boolean.TRUE, servletContextAttributes.get(FrontendCacheVersion.DEV_MODE_CACHE_ATTR));
    assertTrue(!servletContextAttributes.containsKey(BundleTag.MANIFEST_CACHE_ATTR));
    verify(servletContext, never()).getResourceAsStream(BundleTag.VITE_MANIFEST_PATH);
    verify(servletContext).removeAttribute(BundleTag.MANIFEST_CACHE_ATTR);
  }

  @Test
  public void missingBundleRefreshesManifestCacheBeforeThrowing() throws JspException {
    BundleTag realTag =
        new BundleTag() {
          @Override
          Set<String> getRenderedAssetKeys() {
            return renderedAssets;
          }
        };
    realTag.setPageContext(pageContext);
    BundleTag.ChunkManifest staleManifest =
        BundleTag.ChunkManifest.fromBundles(
            Collections.singletonMap(
                "toastMessage",
                new BundleTag.ChunkManifest.BundleAssets(
                    List.of(), List.of(), List.of("/ui/dist/toastMessage-abc123.js"))));
    doReturn(staleManifest).when(servletContext).getAttribute(BundleTag.MANIFEST_CACHE_ATTR);
    when(servletContext.getResourceAsStream(BundleTag.VITE_MANIFEST_PATH))
        .thenReturn(manifestStream("appBar", "appBar-abc123.js"));

    realTag.setBundle("appBar");

    assertEquals(TagSupport.SKIP_BODY, realTag.doStartTag());
    assertTrue(output.toString().contains("type=\"module\" src=\"/ui/dist/appBar-abc123.js\""));
    verify(servletContext)
        .setAttribute(
            org.mockito.ArgumentMatchers.eq(BundleTag.MANIFEST_CACHE_ATTR),
            org.mockito.ArgumentMatchers.any(BundleTag.ChunkManifest.class));
  }

  @Test
  public void devModeRefreshesManifestOncePerRequest() throws JspException {
    class DevModeBundleTag extends BundleTag {
      private final List<ChunkManifest> manifests =
          List.of(
              ChunkManifest.fromBundles(
                  Collections.singletonMap(
                      "appBar",
                      new ChunkManifest.BundleAssets(
                          List.of(), List.of(), List.of("/ui/dist/appBar-first.js")))),
              ChunkManifest.fromBundles(
                  Collections.singletonMap(
                      "appBar",
                      new ChunkManifest.BundleAssets(
                          List.of(), List.of(), List.of("/ui/dist/appBar-second.js")))));
      private int refreshCount;

      @Override
      boolean isDevMode() {
        return true;
      }

      @Override
      ChunkManifest refreshManifestCache() {
        ChunkManifest nextManifest = manifests.get(refreshCount);
        refreshCount++;
        requestAttributes.remove(REQUEST_MANIFEST_CACHE_ATTR);
        return nextManifest;
      }
    }

    DevModeBundleTag realTag = new DevModeBundleTag();
    realTag.setPageContext(pageContext);
    realTag.setBundle("appBar");

    assertEquals(TagSupport.SKIP_BODY, realTag.doStartTag());
    assertTrue(output.toString().contains("src=\"/ui/dist/appBar-first.js\""));
    assertEquals(1, realTag.refreshCount);

    output.setLength(0);
    requestAttributes.clear();
    assertEquals(TagSupport.SKIP_BODY, realTag.doStartTag());
    assertTrue(output.toString().contains("src=\"/ui/dist/appBar-second.js\""));
    assertEquals(2, realTag.refreshCount);
  }

  @Test
  public void hmrModeRendersSameOriginViteClientAndEntrypointScripts() throws JspException {
    BundleTag realTag =
        new BundleTag() {
          @Override
          boolean isDevMode() {
            return true;
          }

          @Override
          boolean isReactDevMode() {
            return true;
          }

          @Override
          Map<String, String> getEntrypoints() {
            return Collections.singletonMap("appBar", "src/eln/AppBar.tsx");
          }

          @Override
          Set<String> getRenderedAssetKeys() {
            return renderedAssets;
          }
        };
    realTag.setPageContext(pageContext);
    realTag.setBundle("appBar");

    assertEquals(TagSupport.SKIP_BODY, realTag.doStartTag());
    assertFalse(output.toString().contains("/ui/dist/@react-refresh"));
    assertFalse(
        output.toString().contains("window.__vite_plugin_react_preamble_installed__ = true;"));
    assertTrue(output.toString().contains("src=\"/ui/dist/@vite/client\""));
    assertTrue(output.toString().contains("src=\"/ui/dist/src/eln/AppBar.tsx\""));
  }

  @Test
  public void devModeReusesRefreshedManifestWithinSingleRequest() {
    class DevModeBundleTag extends BundleTag {
      private int refreshCount;

      @Override
      boolean isDevMode() {
        return true;
      }

      @Override
      ChunkManifest refreshManifestCache() {
        refreshCount++;
        requestAttributes.remove(REQUEST_MANIFEST_CACHE_ATTR);
        return ChunkManifest.fromBundles(
            Collections.singletonMap(
                "appBar",
                new ChunkManifest.BundleAssets(
                    List.of(), List.of(), List.of("/ui/dist/appBar-latest.js"))));
      }
    }

    DevModeBundleTag realTag = new DevModeBundleTag();
    realTag.setPageContext(pageContext);

    BundleTag.ChunkManifest firstLookup = realTag.getManifestCache();
    BundleTag.ChunkManifest secondLookup = realTag.getManifestCache();

    assertEquals(firstLookup, secondLookup);
    assertEquals(1, realTag.refreshCount);
  }

  private ByteArrayInputStream manifestStream(String bundleName, String scriptPath) {
    String json =
        "{"
            + "\"src/entries/"
            + bundleName
            + ".tsx\":{"
            + "\"file\":\""
            + scriptPath
            + "\","
            + "\"name\":\""
            + bundleName
            + "\","
            + "\"src\":\"src/entries/"
            + bundleName
            + ".tsx\","
            + "\"isEntry\":true,"
            + "\"imports\":[\"chunks/shared.js\"],"
            + "\"css\":[\"assets/"
            + bundleName
            + ".css\"]"
            + "},"
            + "\"chunks/shared.js\":{"
            + "\"file\":\"chunks/shared.js\","
            + "\"css\":[\"assets/shared.css\"]"
            + "}"
            + "}";
    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
  }

  private ByteArrayInputStream entrypointsStream() {
    String json = "{\"appBar\":\"src/eln/AppBar.tsx\",\"apps\":\"src/eln/apps/index.tsx\"}";
    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
  }
}
