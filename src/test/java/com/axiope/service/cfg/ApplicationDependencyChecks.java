package com.axiope.service.cfg;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.AbstractJDependTest;
import org.junit.Test;

/**
 * Performs tests on package layering. The test is in this packge, because this is the highest
 * layered package as it configures Spring beans across the application.
 */
public class ApplicationDependencyChecks extends AbstractJDependTest {
  static final String ANY_WEB_LAYER_PACKAGE = "webapp";
  static final String ANY_HIBERNATE_IMPL = "dao.hibernate";
  static final String LIQUIBASE = "customliquibaseupdates";
  static final String SERVICE_LAYER = "service";
  static final String IMAGE_UTILS = "com.researchspace.imageutils";

  @Test
  public void serviceAPIDoesNotDependonHibernateOrWebLayer() {
    String[] servicePackages =
        new String[] {
          "com.researchspace.service",
          "com.researchspace.service.impl",
          "com.researchspace.analytics.service.impl",
          "com.researchspace.analytics.service",
          "com.researchspace.admin.service",
          "com.researchspace.admin.service.impl",
          "com.researchspace.maintenance.service",
          "com.researchspace.maintenance.service.impl",
        };
    assertTrue(
        "Invalid dependency!",
        assertDoesNotDependOn(
            servicePackages,
            false,
            new String[] {
              ANY_WEB_LAYER_PACKAGE, ANY_HIBERNATE_IMPL, LIQUIBASE,
            }));
  }

  @Test
  public void ModelDoesNotDependonHibernateOrServiceOrWebLayer() {
    String[] modelPackages =
        new String[] {
          "com.researchspace.model",
        };
    assertTrue(
        "Invalid dependency!",
        assertDoesNotDependOn(
            modelPackages,
            false,
            new String[] {
              ANY_WEB_LAYER_PACKAGE, ANY_HIBERNATE_IMPL, LIQUIBASE, SERVICE_LAYER, IMAGE_UTILS
            }));
  }

  @Test
  public void DAODoesNotDependOnWebORServiceLayer() {
    String[] servicePackages =
        new String[] {
          "com.researchspace.dao",
        };
    // exact match for dao package only just now
    assertTrue(
        "Invalid dependency!",
        assertDoesNotDependOn(
            servicePackages,
            true,
            new String[] {ANY_WEB_LAYER_PACKAGE, ANY_HIBERNATE_IMPL, LIQUIBASE, SERVICE_LAYER}));
  }
}
