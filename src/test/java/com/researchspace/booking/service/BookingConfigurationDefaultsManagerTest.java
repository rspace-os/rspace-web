package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDefaultsDao;
import com.researchspace.dao.resourceaccess.ResourceAccessDao;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDefaultAccessGrantee;
import com.researchspace.model.booking.BookingDefaultSharedWith;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.service.JsonMessageSource;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class BookingConfigurationDefaultsManagerTest {

  private final BookingConfigurationDefaultsDao dao = mock(BookingConfigurationDefaultsDao.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final ResourceAccessDao resourceAccessDao = mock(ResourceAccessDao.class);
  private final LocalValidatorFactoryBean validator = validator();
  private final BookingConfigurationDefaultsManager manager =
      new BookingConfigurationDefaultsManagerImpl(dao, resourceAccessDao, validator, events);

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

  @Test
  void resolvesAndReplacesSelectedDefaultGranteesServerSide() {
    User sysadmin = sysadmin();
    User selected = mock(User.class);
    when(selected.getId()).thenReturn(42L);
    when(selected.getDisplayName()).thenReturn("Ada Lovelace");
    when(selected.getUsername()).thenReturn("ada");
    BookingConfigurationDefaults defaults = defaults();
    when(dao.lockSingleton()).thenReturn(Optional.of(defaults));
    when(dao.saveAndFlush(defaults)).thenReturn(defaults);
    ResourceRoleAssignment selectedBooker =
        ResourceRoleAssignment.forUser(BookingResourceRoleScheme.BOOKER, selected);
    when(resourceAccessDao.resolveAvailable("user:42", BookingResourceRoleScheme.BOOKER))
        .thenReturn(selectedBooker);

    BookingConfigurationDefaults updated =
        manager.updateDefaults(
            BookingSchedulingSettings.Patch.empty(),
            BookingDisplaySettings.Patch.empty(),
            BookingDefaultSharedWith.SELECTED,
            List.of("user:42"),
            0,
            sysadmin,
            sysadmin);

    assertEquals(BookingDefaultSharedWith.SELECTED, updated.getDefaultSharedWith());
    assertEquals(
        List.of("user:42"),
        updated.getSelectedAccessGrantees().stream()
            .map(BookingDefaultAccessGrantee::getGranteeKey)
            .toList());
  }

  @Test
  void rejectsContradictoryEmptyDuplicateAndUnavailableSelections() {
    User sysadmin = sysadmin();
    BookingConfigurationDefaults defaults = defaults();
    when(dao.lockSingleton()).thenReturn(Optional.of(defaults));

    assertThrows(
        InvalidBookingDefaultSharingException.class,
        () ->
            manager.updateDefaults(
                BookingSchedulingSettings.Patch.empty(),
                BookingDisplaySettings.Patch.empty(),
                BookingDefaultSharedWith.SELECTED,
                List.of(),
                0,
                sysadmin,
                sysadmin));
    assertThrows(
        InvalidBookingDefaultSharingException.class,
        () ->
            manager.updateDefaults(
                BookingSchedulingSettings.Patch.empty(),
                BookingDisplaySettings.Patch.empty(),
                BookingDefaultSharedWith.ONLY_ME,
                List.of("user:42"),
                0,
                sysadmin,
                sysadmin));
    assertThrows(
        InvalidBookingDefaultSharingException.class,
        () ->
            manager.updateDefaults(
                BookingSchedulingSettings.Patch.empty(),
                BookingDisplaySettings.Patch.empty(),
                BookingDefaultSharedWith.SELECTED,
                List.of("user:42", "user:42"),
                0,
                sysadmin,
                sysadmin));
    assertThrows(
        InvalidBookingDefaultSharingException.class,
        () ->
            manager.updateDefaults(
                BookingSchedulingSettings.Patch.empty(),
                BookingDisplaySettings.Patch.empty(),
                BookingDefaultSharedWith.SELECTED,
                List.of("user:999"),
                0,
                sysadmin,
                sysadmin));
  }

  @Test
  void retainsAnExistingSelectionWithoutReResolvingAndClearsItForOnlyMe() {
    User sysadmin = sysadmin();
    User selected = mock(User.class);
    when(selected.getId()).thenReturn(42L);
    when(selected.getDisplayName()).thenReturn("Ada Lovelace");
    when(selected.getUsername()).thenReturn("ada");
    BookingConfigurationDefaults defaults = defaults();
    defaults.setDefaultSharedWith(BookingDefaultSharedWith.SELECTED);
    defaults.addSelectedAccessGrantee(BookingDefaultAccessGrantee.forUser(selected));
    when(dao.lockSingleton()).thenReturn(Optional.of(defaults));
    when(dao.saveAndFlush(defaults)).thenReturn(defaults);

    manager.updateDefaults(
        BookingSchedulingSettings.Patch.empty(),
        BookingDisplaySettings.Patch.empty(),
        BookingDefaultSharedWith.SELECTED,
        List.of("user:42"),
        0,
        sysadmin,
        sysadmin);
    verify(resourceAccessDao, never())
        .resolveAvailable("user:42", BookingResourceRoleScheme.BOOKER);

    manager.updateDefaults(
        BookingSchedulingSettings.Patch.empty(),
        BookingDisplaySettings.Patch.empty(),
        BookingDefaultSharedWith.ONLY_ME,
        null,
        0,
        sysadmin,
        sysadmin);
    assertEquals(BookingDefaultSharedWith.ONLY_ME, defaults.getDefaultSharedWith());
    assertTrue(defaults.getSelectedAccessGrantees().isEmpty());
  }

  private static User sysadmin() {
    User sysadmin = mock(User.class);
    when(sysadmin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    return sysadmin;
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
