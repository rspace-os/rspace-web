package com.researchspace.model;

import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

public class AllToStringMethodsTest {
  Logger logger = LoggerFactory.getLogger(AllToStringMethodsTest.class);

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void callingToStringOnNewObjectsDoesntThrowNPE() {
    // tests toString for NPEs in classes that can be instantiated by reflection (public no-args
    // constructor)
    StringBuffer sb = new StringBuffer();
    callToStrings("com.axiope", sb);
    callToStrings("com.researchspace", sb);
    assertTrue("Failed classes: " + sb, sb.length() == 0);
  }

  private void callToStrings(String packageRoot, StringBuffer sb) {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(packageRoot + ".*")));
    scanner.addExcludeFilter(
        new RegexPatternTypeFilter(Pattern.compile("com.researchspace.recordsandbox.*")));
    // external client libraries on the classpath share the com.researchspace root but
    // cannot be fixed from this repository
    scanner.addExcludeFilter(
        new RegexPatternTypeFilter(Pattern.compile("com.researchspace.fieldmark.*")));
    scanner.addExcludeFilter(
        new RegexPatternTypeFilter(Pattern.compile("com.researchspace.raid.*")));

    for (BeanDefinition bd : scanner.findCandidateComponents(packageRoot)) {
      String className = bd.getBeanClassName();
      try {
        Object o = Class.forName(className).newInstance();
        o.toString();
      } catch (InstantiationException e) {
        logger.debug(className + " could not be instantiated, skipping");
      } catch (IllegalAccessException e) {
        logger.debug(className + " could not be accessed, skipping");
      } catch (ClassNotFoundException | NoClassDefFoundError e) {
        logger.debug(className + " could not be found, skipping");
      } catch (IllegalStateException e) {
        logger.debug(className + " threw illegal state exception, skipping", e);
      } catch (NullPointerException npe) {
        sb.append(className).append("\n");
      }
    }
  }
}
