package com.axiope.service.cfg;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.UserDao;
import com.researchspace.service.GlobalInitManager;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.service.impl.BookingFixturesAppInitialiser;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class ProductionConfigTest {

  @Test
  void registersPropertyGatedBookingFixturesInProductionStartup() {
    ProductionConfig config = new ProductionConfig();
    IApplicationInitialisor featureFlagInitialisor = new AbstractAppInitializor();
    IApplicationInitialisor bookingFixtures =
        config.bookingFixtures(
            "development-password",
            mock(UserDao.class),
            mock(ContainerDao.class),
            mock(InstrumentDao.class),
            mock(ContainerApiManager.class),
            mock(InstrumentEntityApiManager.class),
            mock(MessageSourceUtils.class),
            mock(BookingConfigurationManager.class),
            mock(TimeSlotBookingManager.class),
            mock(BookingConfigurationDao.class),
            mock(TimeSlotBookingDao.class),
            mock(PlatformTransactionManager.class),
            false,
            mock(IContentInitializer.class));

    assertAll(
        () ->
            assertIncludesBookingFixtures(
                config.globalInitManager(
                    new AbstractAppInitializor(), featureFlagInitialisor, bookingFixtures)),
        () ->
            assertIncludesBookingFixtures(
                config.globalInitManagerTest(featureFlagInitialisor, bookingFixtures)));
  }

  private static void assertIncludesBookingFixtures(GlobalInitManager manager) {
    assertTrue(
        manager.getApplicationInitialisors().stream()
            .anyMatch(BookingFixturesAppInitialiser.class::isInstance));
  }
}
