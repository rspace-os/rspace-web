package com.researchspace.webapp.config;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Registers the DispatcherServlet programmatically so that multipart upload limits can be resolved
 * at startup, before the Spring context exists. This replaces the static {@code <servlet>} / {@code
 * <multipart-config>} block that was previously in web.xml.
 *
 * <p>Spring 6 removed {@code CommonsMultipartResolver}, which previously read the limit from Spring
 * config. The replacement {@code StandardServletMultipartResolver} has no size setter — limits must
 * be applied at the servlet-container level via {@link MultipartConfigElement}, which is set here
 * at servlet-registration time (before Spring loads, so {@code @Value} is not an option).
 *
 * <p>{@code files.maxUploadSize} caps the <em>total multipart request</em> size, with no separate
 * per-file cap — the same semantics {@code CommonsMultipartResolver.setMaxUploadSize} enforced
 * before the upgrade.
 *
 * <p>Resolution order for {@code files.maxUploadSize}:
 *
 * <ol>
 *   <li>JVM system property {@code -Dfiles.maxUploadSize=<bytes>} if set.
 *   <li>{@code files.maxUploadSize} key in {@code ${propertyFileDir}/deployment.properties} if the
 *       {@code propertyFileDir} JVM system property is set and the file is readable.
 *   <li>Default: 50 MB.
 * </ol>
 *
 * <p>Note: the servlet container's connector may impose its own ceiling (Tomcat's {@code
 * maxSwallowSize} in particular — keep it {@code -1} or larger than the configured upload limit so
 * oversize uploads get a clean 413 rather than a connection reset).
 */
public class DispatcherServletInitializer implements ServletContextListener {

  private static final Logger log = LoggerFactory.getLogger(DispatcherServletInitializer.class);

  static final long DEFAULT_MAX_UPLOAD_SIZE = 52_428_800L; // 50 MB
  static final String PROPERTY_NAME = "files.maxUploadSize";
  static final String PROPERTY_FILE_DIR = "propertyFileDir";
  static final String DEPLOYMENT_PROPERTIES_FILE = "deployment.properties";

  /**
   * Public URL prefixes that can receive multipart uploads. UrlRewriteFilter forwards every public
   * URL to {@code /app/*}, where this dispatcher handles it — but Tomcat resolves a request's
   * multipart configuration from the servlet the request was <em>originally</em> mapped to, not the
   * forward target (Jetty uses the forward target's servlet, which masks this difference under
   * jetty:run). Without these extra mappings, the original match for a public URL is the
   * container's default servlet, which has no multipart config, so Tomcat silently parses the
   * request as having no parts and every upload fails with "Required part ... is not present".
   *
   * <p>Mapping the upload-receiving prefixes to this servlet attaches the {@link
   * MultipartConfigElement} to the originally-matched servlet for those URLs. Routing is unchanged:
   * the rewrite filter still forwards to {@code /app/*} before any servlet is invoked, so these
   * mappings only ever influence multipart parsing.
   *
   * <p>A unit test scans all controllers and fails if a multipart-accepting controller's URL prefix
   * is not covered by this list — extend the list when it does.
   *
   * <p>The scan only sees handler methods that declare a {@code MultipartFile}. Some frontend code
   * submits plain form fields as {@code multipart/form-data} (a bare {@code FormData} body), which
   * hits handlers taking ordinary {@code @RequestParam}/{@code @ModelAttribute} values — those
   * prefixes must be added here manually. {@code /groups/*} is one: group invitations, profile
   * edits, and group permission toggles are all posted as FormData. {@code /integration/*} is
   * another: the Apps page posts {@code optionsId} to {@code /integration/deleteAppOptions} as a
   * bare FormData body.
   */
  static final String[] MULTIPART_SOURCE_PATTERNS = {
    "/api/*",
    "/gallery/*",
    "/export/*",
    "/workspace/*",
    "/system/*",
    "/userform/*",
    "/groups/*",
    "/integration/*"
  };

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    ServletContext ctx = sce.getServletContext();

    long maxUploadSize = resolveMaxUploadSize();

    ServletRegistration.Dynamic dispatcher = ctx.addServlet("dispatcher", new DispatcherServlet());
    dispatcher.setLoadOnStartup(1);
    dispatcher.setAsyncSupported(true);
    dispatcher.addMapping("/app/*");
    Set<String> conflicts = dispatcher.addMapping(MULTIPART_SOURCE_PATTERNS);
    if (!conflicts.isEmpty()) {
      log.warn(
          "Could not map multipart source patterns {} — already mapped to another servlet;"
              + " uploads on those paths will fail under Tomcat",
          conflicts);
    }
    // -1 = no per-file cap; the request cap bounds file size, as CommonsMultipartResolver did.
    dispatcher.setMultipartConfig(new MultipartConfigElement("", -1, maxUploadSize, 0));

    log.info(
        "Registered DispatcherServlet with multipart max-request-size={} bytes", maxUploadSize);
  }

  long resolveMaxUploadSize() {
    Long fromSysProp = parsePositiveLong(System.getProperty(PROPERTY_NAME), "system property");
    if (fromSysProp != null) {
      log.info("Resolved {} from JVM system property: {}", PROPERTY_NAME, fromSysProp);
      return fromSysProp;
    }

    Long fromDeploymentFile = readFromDeploymentProperties();
    if (fromDeploymentFile != null) {
      return fromDeploymentFile;
    }

    log.info("Using default {} of {} bytes", PROPERTY_NAME, DEFAULT_MAX_UPLOAD_SIZE);
    return DEFAULT_MAX_UPLOAD_SIZE;
  }

  private Long readFromDeploymentProperties() {
    String dir = System.getProperty(PROPERTY_FILE_DIR);
    if (dir == null || dir.isBlank()) {
      return null;
    }
    // Strip the Spring Resource "file:" prefix used in prod (e.g.
    // -DpropertyFileDir=file:/etc/rspace/).
    if (dir.startsWith("file:")) {
      dir = dir.substring("file:".length());
    }
    Path file = Paths.get(dir, DEPLOYMENT_PROPERTIES_FILE);
    if (!Files.isReadable(file)) {
      log.warn(
          "{} system property is set but {} is not readable; skipping", PROPERTY_FILE_DIR, file);
      return null;
    }
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      props.load(in);
    } catch (IOException e) {
      log.warn("Failed to read {}: {}; skipping", file, e.getMessage());
      return null;
    }
    Long parsed = parsePositiveLong(props.getProperty(PROPERTY_NAME), file.toString());
    if (parsed != null) {
      log.info("Resolved {} from {}: {}", PROPERTY_NAME, file, parsed);
    }
    return parsed;
  }

  private Long parsePositiveLong(String value, String source) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      long parsed = Long.parseLong(value.trim());
      if (parsed > 0) {
        return parsed;
      }
      log.warn("{} from {} must be positive, got {}; ignoring", PROPERTY_NAME, source, value);
    } catch (NumberFormatException e) {
      log.warn("Could not parse {} value '{}' from {}; ignoring", PROPERTY_NAME, value, source);
    }
    return null;
  }
}
