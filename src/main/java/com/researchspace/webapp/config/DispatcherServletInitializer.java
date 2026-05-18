package com.researchspace.webapp.config;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Registers the DispatcherServlet programmatically so that multipart limits can be read from the
 * JVM system property {@code files.maxUploadSize} at startup. This replaces the static {@code
 * <servlet>} / {@code <multipart-config>} block that was previously in web.xml.
 *
 * <p>Spring 6 removed {@code CommonsMultipartResolver}, which previously read the property from
 * Spring config. The replacement {@code StandardServletMultipartResolver} has no size setter —
 * limits must be applied at the servlet-container level via {@link MultipartConfigElement}.
 */
public class DispatcherServletInitializer implements ServletContextListener {

  private static final Logger log = LoggerFactory.getLogger(DispatcherServletInitializer.class);

  static final long DEFAULT_MAX_FILE_SIZE = 52_428_800L; // 50 MB
  static final String PROPERTY_NAME = "files.maxUploadSize";

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    ServletContext ctx = sce.getServletContext();

    long maxFileSize = resolveMaxFileSize();
    long maxRequestSize = maxFileSize * 2;

    ServletRegistration.Dynamic dispatcher = ctx.addServlet("dispatcher", new DispatcherServlet());
    dispatcher.setLoadOnStartup(1);
    dispatcher.setAsyncSupported(true);
    dispatcher.addMapping("/app/*", "/offline/*");
    dispatcher.setMultipartConfig(new MultipartConfigElement("", maxFileSize, maxRequestSize, 0));

    log.info(
        "Registered DispatcherServlet with multipart max-file-size={} bytes"
            + " (from {} system property: {})",
        maxFileSize,
        PROPERTY_NAME,
        System.getProperty(PROPERTY_NAME) != null ? "yes" : "no, using default");
  }

  private long resolveMaxFileSize() {
    String value = System.getProperty(PROPERTY_NAME);
    if (value != null) {
      try {
        long parsed = Long.parseLong(value);
        if (parsed > 0) {
          return parsed;
        }
        log.warn(
            "{} must be positive, got {}; using default {}",
            PROPERTY_NAME,
            value,
            DEFAULT_MAX_FILE_SIZE);
      } catch (NumberFormatException e) {
        log.warn(
            "Could not parse {} value '{}'; using default {}",
            PROPERTY_NAME,
            value,
            DEFAULT_MAX_FILE_SIZE);
      }
    }
    return DEFAULT_MAX_FILE_SIZE;
  }
}
