package com.researchspace.jetty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jetty.ee10.annotations.AnnotationConfiguration;
import org.eclipse.jetty.ee10.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.ee10.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.ee10.webapp.JspConfiguration;
import org.eclipse.jetty.ee10.webapp.MetaInfConfiguration;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.util.ClassMatcher;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.xml.XmlConfiguration;

/**
 * Runs RSpace on embedded Jetty for the Gradle {@code jettyRun} and {@code jettyRunWar} tasks, as
 * jetty-ee10-maven-plugin's {@code jetty:run} and {@code jetty:run-war} did. See
 * DevDocs/DeveloperNotes/Gradle.md.
 *
 * <p>Options:
 *
 * <ul>
 *   <li>{@code --webapp <dir> --classpath-file <file>}: serve an exploded webapp, with the class
 *       directories and jars listed one per line in the file as its classpath;
 *   <li>{@code --war <file>}: serve a WAR;
 *   <li>{@code --context-path <path>}, {@code --temp-dir <dir>};
 *   <li>{@code --jetty-xml <file>}, repeatable: configure the server from Jetty XML files instead
 *       of adding the default HTTP connector;
 *   <li>{@code --redeploy-on-stdin}: restart the webapp whenever a line is read from stdin, as
 *       jetty:run does with {@code <scan>0</scan>}.
 * </ul>
 *
 * The HTTP port comes from the {@code jetty.http.port} system property, default 8080.
 */
public final class JettyLauncher {

  private static final String JETTY_SLF4J_PROVIDER =
      "org.eclipse.jetty.logging.JettyLoggingServiceProvider";

  private JettyLauncher() {}

  public static void main(String[] args) throws Exception {
    Map<String, String> options = new HashMap<>();
    List<String> jettyXmls = new ArrayList<>();
    boolean redeployOnStdin = false;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--jetty-xml" -> jettyXmls.add(args[++i]);
        case "--redeploy-on-stdin" -> redeployOnStdin = true;
        default -> {
          if (!args[i].startsWith("--") || i + 1 >= args.length) {
            throw new IllegalArgumentException("Unexpected argument: " + args[i]);
          }
          options.put(args[i].substring(2), args[++i]);
        }
      }
    }

    Server server = new Server();
    server.setStopAtShutdown(true);
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    server.setHandler(new Handler.Sequence(contexts, new DefaultHandler()));

    if (jettyXmls.isEmpty()) {
      ServerConnector connector = new ServerConnector(server);
      connector.setPort(Integer.getInteger("jetty.http.port", 8080));
      connector.setHost(System.getProperty("jetty.http.host"));
      server.addConnector(connector);
    } else {
      applyJettyXmls(server, jettyXmls);
    }

    try (ResourceFactory.Closeable resources = ResourceFactory.closeable()) {
      WebAppContext webapp = createWebApp(options, resources);
      contexts.addHandler(webapp);
      server.start();
      for (Connector connector : server.getConnectors()) {
        if (connector instanceof ServerConnector serverConnector) {
          System.out.println(
              "[jetty] Started "
                  + String.join("/", serverConnector.getProtocols())
                  + " on port "
                  + serverConnector.getLocalPort());
        }
      }
      if (redeployOnStdin) {
        startRedeployThread(webapp);
      }
      server.join();
    }
  }

  private static WebAppContext createWebApp(Map<String, String> options, ResourceFactory resources)
      throws IOException {
    WebAppContext webapp = new WebAppContext();
    webapp.setContextPath(options.getOrDefault("context-path", "/"));
    webapp.addConfiguration(
        new EnvConfiguration(),
        new PlusConfiguration(),
        new AnnotationConfiguration(),
        new JspConfiguration());
    // Container jars whose TLDs and resources the webapp sees, as jetty-ee10-maven-plugin sets.
    webapp.setAttribute(
        MetaInfConfiguration.CONTAINER_JAR_PATTERN,
        ".*/jakarta.servlet-api-[^/]*\\.jar$|.*/jakarta.servlet.jsp.jstl-[^/]*\\.jar$"
            + "|.*/jakarta.faces-[^/]*\\.jar$");
    // The webapp loads its own slf4j and log4j2 binding. Hide Jetty's slf4j provider jar, whose
    // META-INF/services entry the webapp's slf4j would otherwise find but could not load.
    try {
      webapp.addHiddenClassMatcher(
          new ClassMatcher(
              Class.forName(JETTY_SLF4J_PROVIDER)
                  .getProtectionDomain()
                  .getCodeSource()
                  .getLocation()
                  .toURI()
                  .toString()));
    } catch (ClassNotFoundException | URISyntaxException e) {
      System.err.println("[jetty] Could not hide " + JETTY_SLF4J_PROVIDER + ": " + e);
    }
    if (options.containsKey("temp-dir")) {
      Path tempDir = Path.of(options.get("temp-dir"));
      Files.createDirectories(tempDir);
      webapp.setTempDirectory(tempDir.toFile());
      webapp.setTempDirectoryPersistent(false);
    }

    if (options.containsKey("war")) {
      webapp.setWar(options.get("war"));
    } else {
      String webappDir = require(options, "webapp");
      webapp.setBaseResource(resources.newResource(Path.of(webappDir)));
      List<Resource> classpath = new ArrayList<>();
      for (String line :
          Files.readAllLines(Path.of(require(options, "classpath-file")), StandardCharsets.UTF_8)) {
        Path entry = Path.of(line);
        if (!line.isBlank() && Files.exists(entry)) {
          classpath.add(resources.newResource(entry));
        }
      }
      webapp.setExtraClasspath(classpath);
    }
    return webapp;
  }

  /**
   * Applies Jetty XML files in order, sharing their ids, as jetty-ee10-maven-plugin's {@code
   * jettyXmls} does. System properties are available as {@code <Property>} values, and {@code
   * jetty.http.port} also sets {@code jetty.port}, the HTTP port in jetty/jetty-http.xml.
   */
  private static void applyJettyXmls(Server server, List<String> jettyXmls) throws Exception {
    Map<String, String> properties = new HashMap<>();
    System.getProperties().forEach((k, v) -> properties.put(k.toString(), v.toString()));
    properties.putIfAbsent("jetty.base", System.getProperty("user.dir"));
    String httpPort = System.getProperty("jetty.http.port");
    if (httpPort != null) {
      properties.putIfAbsent("jetty.port", httpPort);
    }
    Map<String, Object> ids = new HashMap<>();
    ids.put("Server", server);
    try (ResourceFactory.Closeable resources = ResourceFactory.closeable()) {
      for (String xml : jettyXmls) {
        XmlConfiguration configuration = new XmlConfiguration(resources.newResource(Path.of(xml)));
        configuration.getIdMap().putAll(ids);
        configuration.getProperties().putAll(properties);
        configuration.configure();
        ids.putAll(configuration.getIdMap());
      }
    }
  }

  /** Restarts the webapp, reloading its classes, on each line read from stdin. */
  private static void startRedeployThread(WebAppContext webapp) {
    Thread thread =
        new Thread(
            () -> {
              try (BufferedReader in =
                  new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                System.out.println("[jetty] Press ENTER to redeploy the webapp.");
                while (in.readLine() != null) {
                  System.out.println("[jetty] Redeploying the webapp ...");
                  try {
                    // Session attributes belong to the old webapp classloader. Match Maven's
                    // clean login redirect after redeploy instead of retaining incompatible objects.
                    webapp.getSessionHandler().getSessionCache().setInvalidateOnShutdown(true);
                    webapp.stop();
                    // Drop the servlets, filters and listeners remembered from the last start,
                    // which would otherwise be registered twice, as jetty:run's redeploy does.
                    webapp.setServletHandler(new ServletHandler());
                    webapp.start();
                    System.out.println("[jetty] Redeployed the webapp.");
                  } catch (Exception e) {
                    System.err.println("[jetty] Redeploy failed: " + e);
                    e.printStackTrace();
                  }
                }
              } catch (IOException e) {
                System.err.println("[jetty] Stopped reading stdin for redeploys: " + e);
              }
            },
            "jetty-redeploy-on-stdin");
    thread.setDaemon(true);
    thread.start();
  }

  private static String require(Map<String, String> options, String name) {
    String value = options.get(name);
    if (value == null) {
      throw new IllegalArgumentException("Missing --" + name);
    }
    return value;
  }
}
