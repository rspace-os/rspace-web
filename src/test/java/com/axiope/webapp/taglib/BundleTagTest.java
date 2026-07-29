package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockJspWriter;
import org.springframework.mock.web.MockServletContext;

@ExtendWith(MockitoExtension.class)
public class BundleTagTest {

  @Mock private PageContext pageContext;

  private final StringWriter output = new StringWriter();
  private final Set<String> renderedAssets = new LinkedHashSet<>();
  private MockHttpServletRequest request;
  private MockServletContext servletContext;
  private JspWriter writer;
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

  @BeforeEach
  public void setUp() {
    originalReactDevModeProperty = System.getProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
    System.clearProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
    manifest = BundleTag.ChunkManifest.fromBundles(new LinkedHashMap<>());
    output.getBuffer().setLength(0);
    renderedAssets.clear();
    servletContext = spy(new MockServletContext());
    request = new MockHttpServletRequest(servletContext);
    writer = new MockJspWriter(output);
    tag = new TestBundleTag();
    tag.setPageContext(pageContext);
  }

  @AfterEach
  public void tearDown() {
    if (originalReactDevModeProperty == null) {
      System.clearProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
    } else {
      System.setProperty(
          FrontendCacheVersion.REACT_DEV_MODE_PROPERTY, originalReactDevModeProperty);
    }
  }

  private void stubRequest() {
    when(pageContext.getRequest()).thenReturn(request);
  }

  private void stubServletContext() {
    when(pageContext.getServletContext()).thenReturn(servletContext);
  }

  private void stubWriter() {
    when(pageContext.getOut()).thenReturn(writer);
  }

  @Test
  public void rendersManifestBackedAssets() throws JspException {
    stubServletContext();
    stubWriter();
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
  public void i18nMessagesTagRendersClassicViteBundle() throws JspException {
    manifest =
        BundleTag.ChunkManifest.fromBundles(
            Collections.singletonMap(
                "legacyI18n",
                new BundleTag.ChunkManifest.BundleAssets(
                    List.of(), List.of(), List.of("/ui/dist/legacyI18n-abc123.js"))));
    I18nMessagesTag i18nTag =
        new I18nMessagesTag() {
          @Override
          ChunkManifest getManifestCache() {
            return manifest;
          }

          @Override
          Set<String> getRenderedAssetKeys() {
            return renderedAssets;
          }
        };
    i18nTag.setPageContext(pageContext);

    assertEquals(TagSupport.SKIP_BODY, i18nTag.doStartTag());
    // The catalogue comes first only because it is written first; RS.msg reads both at call time.
    assertEquals(
        "<script src=\"/ui/dist/legacyMessages.en-US.js\"></script>"
            + "<script src=\"/ui/dist/legacyI18n-abc123.js\"></script>",
        output.toString());
  }

  @Test
  public void i18nMessagesTagCacheBustsTheCatalogueUrl() throws JspException {
    servletContext.setAttribute(FrontendCacheVersion.CACHE_VERSION_ATTR, "1.2.3");
    I18nMessagesTag i18nTag =
        new I18nMessagesTag() {
          @Override
          ChunkManifest getManifestCache() {
            return manifest;
          }

          @Override
          Set<String> getRenderedAssetKeys() {
            return renderedAssets;
          }
        };
    i18nTag.setPageContext(pageContext);

    assertEquals("/ui/dist/legacyMessages.en-US.js?v=1.2.3", i18nTag.catalogueUrl());
  }

  @Test
  public void i18nMessagesTagRendersClassicEntrypointInHmrMode() throws JspException {
    I18nMessagesTag i18nTag =
        new I18nMessagesTag() {
          @Override
          boolean isReactDevMode() {
            return true;
          }

          @Override
          Map<String, String> getEntrypoints() {
            return Collections.singletonMap("legacyI18n", "src/modules/common/i18n/legacyI18n.ts");
          }

          @Override
          Set<String> getRenderedAssetKeys() {
            return renderedAssets;
          }
        };
    i18nTag.setPageContext(pageContext);

    assertEquals(TagSupport.SKIP_BODY, i18nTag.doStartTag());
    assertTrue(output.toString().contains("type=\"module\" src=\"/ui/dist/@vite/client\""));
    assertTrue(
        output
            .toString()
            .contains("<script src=\"/ui/dist/src/modules/common/i18n/legacyI18n.ts\"></script>"));
  }

  @Test
  public void renderingSameBundleTwiceDeduplicatesAssets() throws JspException {
    stubServletContext();
    stubWriter();
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
    stubRequest();
    stubServletContext();
    tag.setBundle("apps");

    assertTrue(
        assertThrows(JspException.class, () -> tag.doStartTag())
            .getMessage()
            .contains("No bundle manifest entry found for bundle: apps"));
  }

  @Test
  public void loadsManifestFromDistPath() {
    when(servletContext.getResourceAsStream(BundleTag.VITE_MANIFEST_PATH))
        .thenReturn(manifestStream("appBar", "appBar-abc123.js"));
    clearInvocations(servletContext);

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
    clearInvocations(servletContext);

    Map<String, String> entrypoints = BundleTag.loadEntrypoints(servletContext);

    verify(servletContext).getResourceAsStream("/ui/bundleEntries.json");
    assertEquals("src/eln/AppBar.tsx", entrypoints.get("appBar"));
  }

  @Test
  public void getManifestCacheDoesNotCacheEmptyManifest() {
    stubRequest();
    stubServletContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    BundleTag realTag = new BundleTag();
    realTag.setPageContext(pageContext);
    when(servletContext.getResourceAsStream(BundleTag.VITE_MANIFEST_PATH)).thenReturn(null);
    clearInvocations(servletContext);

    BundleTag.ChunkManifest loadedManifest = realTag.getManifestCache();

    assertTrue(loadedManifest.isEmpty());
    verify(servletContext).removeAttribute(BundleTag.MANIFEST_CACHE_ATTR);
    verify(servletContext, never()).setAttribute(BundleTag.MANIFEST_CACHE_ATTR, loadedManifest);
  }

  @Test
  public void preWarmManifestCacheCachesParsedManifestForNonDevMode() {
    when(servletContext.getResourceAsStream(BundleTag.VITE_MANIFEST_PATH))
        .thenReturn(manifestStream("appBar", "appBar-abc123.js"));
    clearInvocations(servletContext);

    BundleTag.preWarmManifestCache(servletContext, false);

    assertEquals(
        Boolean.FALSE, servletContext.getAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR));
    Object cachedManifest = servletContext.getAttribute(BundleTag.MANIFEST_CACHE_ATTR);
    assertTrue(cachedManifest instanceof BundleTag.ChunkManifest);
    assertEquals(
        List.of("/ui/dist/appBar-abc123.js"),
        ((BundleTag.ChunkManifest) cachedManifest).getBundleAssets("appBar").getScripts());
  }

  @Test
  public void preWarmManifestCacheSkipsBundleCachingInDevMode() {
    BundleTag.preWarmManifestCache(servletContext, true);

    assertEquals(
        Boolean.TRUE, servletContext.getAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR));
    assertNull(servletContext.getAttribute(BundleTag.MANIFEST_CACHE_ATTR));
    verify(servletContext, never()).getResourceAsStream(BundleTag.VITE_MANIFEST_PATH);
    verify(servletContext).removeAttribute(BundleTag.MANIFEST_CACHE_ATTR);
  }

  @Test
  public void missingBundleRefreshesManifestCacheBeforeThrowing() throws JspException {
    stubRequest();
    stubServletContext();
    stubWriter();
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
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContext.setAttribute(BundleTag.MANIFEST_CACHE_ATTR, staleManifest);
    clearInvocations(servletContext);
    when(servletContext.getResourceAsStream(BundleTag.VITE_MANIFEST_PATH))
        .thenReturn(manifestStream("appBar", "appBar-abc123.js"));
    clearInvocations(servletContext);

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
    stubRequest();
    stubServletContext();
    stubWriter();
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
        request.removeAttribute(REQUEST_MANIFEST_CACHE_ATTR);
        return nextManifest;
      }
    }

    DevModeBundleTag realTag = new DevModeBundleTag();
    realTag.setPageContext(pageContext);
    realTag.setBundle("appBar");

    assertEquals(TagSupport.SKIP_BODY, realTag.doStartTag());
    assertTrue(output.toString().contains("src=\"/ui/dist/appBar-first.js\""));
    assertEquals(1, realTag.refreshCount);

    output.getBuffer().setLength(0);
    request.clearAttributes();
    assertEquals(TagSupport.SKIP_BODY, realTag.doStartTag());
    assertTrue(output.toString().contains("src=\"/ui/dist/appBar-second.js\""));
    assertEquals(2, realTag.refreshCount);
  }

  @Test
  public void hmrModeRendersSameOriginViteClientAndEntrypointScripts() throws JspException {
    stubWriter();
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
    stubRequest();
    class DevModeBundleTag extends BundleTag {
      private int refreshCount;

      @Override
      boolean isDevMode() {
        return true;
      }

      @Override
      ChunkManifest refreshManifestCache() {
        refreshCount++;
        request.removeAttribute(REQUEST_MANIFEST_CACHE_ATTR);
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
