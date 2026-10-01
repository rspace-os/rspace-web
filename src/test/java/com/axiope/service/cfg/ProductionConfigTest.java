package com.axiope.service.cfg;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.service.GlobalInitManager;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.service.impl.BookingFixturesAppInitialiser;
import org.junit.jupiter.api.Test;

class ProductionConfigTest {

  @Test
  void registersPropertyGatedBookingFixturesInProductionStartup() {
    ProductionConfig config = new ProductionConfig();

    assertAll(
        () -> assertIncludesBookingFixtures(config.globalInitManager(new AbstractAppInitializor())),
        () -> assertIncludesBookingFixtures(config.globalInitManagerTest()));
  }

  private static void assertIncludesBookingFixtures(GlobalInitManager manager) {
    assertTrue(
        manager.getApplicationInitialisors().stream()
            .anyMatch(BookingFixturesAppInitialiser.class::isInstance));
  }
}
