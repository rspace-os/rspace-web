package com.researchspace.service.impl;

import com.axiope.search.FileSearchStrategy;
import com.axiope.search.IFileIndexer;
import com.axiope.search.IFileSearcher;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.*;

/** Runs various tests on the application environment prior to startup. */
public class SanityChecker extends AbstractAppInitializor {

  /*
   * For testing
   * @param writer the writer to set
   */
  @Autowired IFileIndexer fileIndexer;

  public void setWriter(IFileIndexer writer) {
    this.fileIndexer = writer;
  }

  @Autowired FileSearchStrategy searchStrategy;

  public void setSearchStrategy(FileSearchStrategy searchStrategy) {
    this.searchStrategy = searchStrategy;
  }

  @Autowired IFileSearcher searcher;

  public void setSearcher(IFileSearcher searcher) {
    this.searcher = searcher;
  }

  @Autowired IPropertyHolder properties;

  public void setProperties(IPropertyHolder properties) {
    this.properties = properties;
  }

  @Autowired private ApplicationContext context;

  public void setUserDao(UserDao userDao) {
    this.userDao = userDao;
  }

  /**
   * This message is used by external scripts at application start-up time to determine if the
   * application has started properly or not. <b>Do not change the text of this field.</b>
   */
  public static final String SANITY_CHECK_RUN_ALL_OK_MSG = "Sanity check run: ALL_OK=";

  @Autowired private UserDao userDao;

  /** Calls */
  @Override
  public void onAppStartup(ApplicationContext context) {
    log.info("Starting sanity check ...");
    boolean ALL_OK = true;
    try {
      logEnvironmentAndProperties(); // should be first, in case something goes wrong later...
      ALL_OK =
          assertSysadmin1UserPresent()
              && assertIsValidDeployment(context)
              && assertLuceneFullTextSearchIndexFolderIsUsable();
    } catch (Throwable ex) {
      fatalStartUpLog("Exception at startup: " + ex.getMessage());
      ALL_OK = false;
    } finally {
      log.info(SANITY_CHECK_RUN_ALL_OK_MSG + ALL_OK);
    }
  }

  private boolean assertLuceneFullTextSearchIndexFolderIsUsable() {
    log.info("Checking Lucene attachment search folder can be set....");
    boolean rc = false;
    try {
      // this just triggers initialisation if need be.
      fileIndexer.init(false); // don't delete index if there already is one
      if (searchStrategy.isLocal()) {
        searchStrategy.searchFiles("a search string", null);
        log.info("Search index folder set as [" + searcher.getIndexFolderPath() + "]");
      }
      rc = true;
    } catch (IOException ie) {
      fatalStartUpLog(" IO error initialising   attachment search.. " + ie.getMessage());
    } catch (Exception e) {
      fatalStartUpLog("Unexpected error.. " + e.getMessage());
    }
    return rc;
  }

  private void logEnvironmentAndProperties() {
    logEnvironment();
    logSystemProperties();
    logDeploymentProperties();
  }

  private void logSystemProperties() {
    log.info("Listing system properties....");
    StringBuffer sb = getSystemPropertiesAsString();
    log.info(sb.toString());
  }

  private void logEnvironment() {
    log.info("Listing environment variables....");
    StringBuffer sb = getEnvironmentVarsAsString();
    log.info(sb.toString());
  }

  private boolean assertIsValidDeployment(ApplicationContext context) {
    // throws exception if not found
    if (properties.isCloud() && properties.isSSO()) {
      fatalStartUpLog(
          "Invalid deployment configuration : cloud deployment is incompatible with SSO:"
              + properties.toString());
      return false;
    } else {
      log.info("Property configuration is valid: " + properties.toString());
      return true;
    }
  }

  private boolean assertSysadmin1UserPresent() {
    boolean rc = true;

    User sysadmin = userDao.getUserByUserName(SYSADMIN_UNAME);
    if (sysadmin == null) {
      fatalStartUpLog("No default sysadmin user found!");
      rc = false;
    } else {
      log.info("Default sysadmin user present");
    }
    return rc;
  }

  // logs resolved deployment.properties, screening secrets/passwords
  private void logDeploymentProperties() {

    log.info("Resolved deployment properties:");
    // this is a bit hacky. Spring has several mechanisms to resolve property file.
    // the modern way using environment doesn't detect these properties. We are using an old way.
    // We have to resolve the property order manually. Each instance of a bean contains
    // configuration of
    // 1 deployment file
    TreeMap<String, String> resolved = getResolvedProperties();
    log.info(deploymentPropertiesToString(resolved));
  }

  TreeMap<String, String> getResolvedProperties() {
    Map<String, PropertySourcesPlaceholderConfigurer> beansOfType =
        context.getBeansOfType(PropertySourcesPlaceholderConfigurer.class);
    // to list sorted alphabetically
    TreeMap<String, String> resolved = new TreeMap<>();

    for (PropertySourcesPlaceholderConfigurer pspc : beansOfType.values()) {

      // 'localProperties' holds RSpace property files
      PropertySource<Properties> localProperties =
          (PropertySource<Properties>)
              pspc.getAppliedPropertySources().stream()
                  .filter(ps -> ps.getName().equals("localProperties"))
                  .findFirst()
                  .orElse(new MapPropertySource("missing", Map.of()));
      Properties ps = localProperties.getSource();
      // 1st defined takes precedence.
      for (Map.Entry prop : ps.entrySet()) {
        if (!resolved.containsKey(prop.getKey())) {
          resolved.put((String) prop.getKey(), prop.getValue().toString());
        }
      }
    }
    return resolved;
  }

  String deploymentPropertiesToString(Map<String, String> resolved) {
    final int meanPropertyLengthGuess = 20;
    StringBuilder sb = new StringBuilder(resolved.size() * meanPropertyLengthGuess);
    for (Map.Entry e : resolved.entrySet()) {
      sb.append(e.getKey() + ": " + desensitiseValue(e) + "\n");
    }
    return sb.toString();
  }

  private static final String[] sensitiveNames =
      new String[] {"key", "secret", "password", "token", "client"};

  // don't show all of sensitive values
  private Object desensitiseValue(Map.Entry<String, String> e) {
    if (StringUtils.containsAny(e.getKey(), sensitiveNames)) {
      return StringUtils.abbreviate(e.getValue(), 6);
    } else {
      return e.getValue();
    }
  }
}
