package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDefaultsDao;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.service.JsonMessageSource;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class BookingConfigurationDefaultsManagerTest {

  private final BookingConfigurationDefaultsDao dao = mock(BookingConfigurationDefaultsDao.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final LocalValidatorFactoryBean validator = validator();
  private final BookingConfigurationDefaultsManager manager =
      new BookingConfigurationDefaultsManagerImpl(dao, validator, events);

  @AfterEach
  void closeValidators() {
    validator.close();
  }

  private static LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(new JsonMessageSource());
    validator.afterPropertiesSet();
    return validator;
  }

  @Test
  void returnsAndAtomicallyPatchesTheRequiredSingleton() {
    User sysadmin = mock(User.class);
    when(sysadmin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    BookingConfigurationDefaults defaults = defaults();
    when(dao.getSafeNull(BookingConfigurationDefaults.SINGLETON_ID))
        .thenReturn(Optional.of(defaults));
    when(dao.lockSingleton()).thenReturn(Optional.of(defaults));
    when(dao.saveAndFlush(defaults)).thenReturn(defaults);

    assertSame(defaults, manager.getDefaults(sysadmin));
    BookingConfigurationDefaults updated =
        manager.updateDefaults(
            new BookingSchedulingSettings.Patch(15L, "08:00", "18:00", 10L, 20L, 120L, true),
            new BookingDisplaySettings.Patch("09:00", "17:00", null, null),
            0,
            sysadmin,
            sysadmin);

    assertEquals(15, updated.getSlotGranularityMinutes());
    assertEquals("08:00", updated.getOpeningStart());
    assertEquals("18:00", updated.getOpeningEnd());
    assertEquals(10, updated.getBufferBeforeMinutes());
    assertEquals(20, updated.getBufferAfterMinutes());
    assertEquals(120, updated.getMaxBookingDurationMinutes());
    assertEquals(true, updated.isAllowDoubleBooking());
    assertEquals("09:00", updated.getAvailabilityWindowStart());
    assertEquals("17:00", updated.getAvailabilityWindowEnd());
    verify(dao).saveAndFlush(defaults);
    verify(events).publishEvent(any(BookingConfigurationDefaultsAuditEvent.class));
  }

  @Test
  void rejectsMissingInvalidAndUnauthorizedSettings() {
    User member = mock(User.class);
    assertThrows(
        AuthorizationException.class,
        () ->
            manager.updateDefaults(
                BookingSchedulingSettings.Patch.empty(),
                BookingDisplaySettings.Patch.empty(),
                0,
                member,
                member));

    User sysadmin = mock(User.class);
    when(sysadmin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    when(dao.getSafeNull(BookingConfigurationDefaults.SINGLETON_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(defaults()));
    when(dao.lockSingleton())
        .thenReturn(Optional.of(defaults()))
        .thenReturn(Optional.of(defaults()));
    assertThrows(IllegalStateException.class, () -> manager.getDefaults(sysadmin));
    assertEquals(
        InvalidBookingSchedulingSettingsException.Reason.OPENING_HOURS,
        assertThrows(
                InvalidBookingSchedulingSettingsException.class,
                () ->
                    manager.updateDefaults(
                        new BookingSchedulingSettings.Patch(
                            null, "18:00", "08:00", null, null, null, null),
                        BookingDisplaySettings.Patch.empty(),
                        0,
                        sysadmin,
                        sysadmin))
            .reason());

    assertEquals(
        InvalidBookingSchedulingSettingsException.Reason.MAXIMUM_DURATION,
        assertThrows(
                InvalidBookingSchedulingSettingsException.class,
                () ->
                    manager.updateDefaults(
                        new BookingSchedulingSettings.Patch(null, null, null, null, null, 7L, null),
                        BookingDisplaySettings.Patch.empty(),
                        0,
                        sysadmin,
                        sysadmin))
            .reason());
  }

  @Test
  void rejectsASecondAdminsStaleVersion() {
    User firstAdmin = mock(User.class);
    User secondAdmin = mock(User.class);
    when(firstAdmin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    when(secondAdmin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    BookingConfigurationDefaults defaults = defaults();
    defaults.setConfigurationVersion(1);
    when(dao.lockSingleton()).thenReturn(Optional.of(defaults));
    when(dao.saveAndFlush(defaults)).thenReturn(defaults);

    manager.updateDefaults(
        new BookingSchedulingSettings.Patch(null, null, null, null, null, null, true),
        BookingDisplaySettings.Patch.empty(),
        1,
        firstAdmin,
        firstAdmin);
    defaults.setConfigurationVersion(2);

    assertThrows(
        StaleBookingSettingsException.class,
        () ->
            manager.updateDefaults(
                new BookingSchedulingSettings.Patch(null, null, null, null, null, null, false),
                BookingDisplaySettings.Patch.empty(),
                1,
                secondAdmin,
                secondAdmin));

    assertEquals(true, defaults.isAllowDoubleBooking());
  }

  private static BookingConfigurationDefaults defaults() {
    BookingConfigurationDefaults defaults = new BookingConfigurationDefaults();
    defaults.setId(BookingConfigurationDefaults.SINGLETON_ID);
    defaults.setSlotGranularityMinutes(5);
    defaults.setOpeningStart("00:00");
    defaults.setOpeningEnd("24:00");
    BookingDisplaySettings.defaults().applyTo(defaults);
    return defaults;
  }
}
