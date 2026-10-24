package com.researchspace.booking.service;

import static com.researchspace.booking.service.BookingConfigurationManagerTestFixtures.defaults;
import static com.researchspace.booking.service.BookingConfigurationManagerTestFixtures.instrument;
import static com.researchspace.testutils.BookingTestSchedules.hours;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingConfigurationDefaultsDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

final class BookingConfigurationManagerTestFixtures {

  static final int OVERSIZED_MUTATION_LIMIT =
      ApiV2BookingConfigurationResource.MUTATION_LIMITS.maxBulkUpdateDeleteRows() + 1;

  final BookingConfigurationDao dao = mock(BookingConfigurationDao.class);
  final BookingConfigurationDefaultsDao defaultsDao = mock(BookingConfigurationDefaultsDao.class);
  final InstrumentDao instrumentDao = mock(InstrumentDao.class);
  final TimeSlotBookingDao timeSlotBookings = mock(TimeSlotBookingDao.class);

  final User actor = mock(User.class);
  final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  final ObjectProvider<ResourceRegistry> resourceRegistry = mock(ObjectProvider.class);
  final ResourceAccessManager accessManager = mock(ResourceAccessManager.class);
  final LocalValidatorFactoryBean validator = BookingSettingsTestFixtures.validator();
  final BookingConfigurationManager manager =
      new BookingConfigurationManagerImpl(
          dao,
          defaultsDao,
          instrumentDao,
          validator,
          events,
          resourceRegistry,
          ApiV2BookingConfigurationResource.DESCRIPTION,
          accessManager,
          new MessageSourceUtils(new JsonMessageSource()),
          timeSlotBookings);

  void setUp() {
    when(dao.saveAndFlush(any(BookingConfiguration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(actor.hasSysadminRole()).thenReturn(true);
    when(actor.isEnabled()).thenReturn(true);
    when(actor.getId()).thenReturn(1L);
    when(actor.getDisplayName()).thenReturn("System Administrator");
    when(actor.getUsername()).thenReturn("sysadmin");
    when(defaultsDao.getSafeNull(BookingConfigurationDefaults.SINGLETON_ID))
        .thenReturn(Optional.of(defaults(5, "00:00", "24:00", 0, 0, 0, false)));
    when(instrumentDao.lockById(any()))
        .thenAnswer(invocation -> Optional.of(instrument(invocation.getArgument(0), actor)));
    when(resourceRegistry.getObject())
        .thenReturn(
            new ResourceRegistry(
                List.of(
                    ApiV2BookingConfigurationResource.DESCRIPTION,
                    ApiV2BookingInstrumentResource.DESCRIPTION,
                    ApiV2InstrumentResource.DESCRIPTION,
                    ApiV2UserResource.DESCRIPTION)));
    ResolvedResourceAccess ownerAccess =
        new ResolvedResourceAccess(
            Optional.of(BookingResourceRoleScheme.OWNER),
            Set.of(
                BookingResourceRoleScheme.READ_RESOURCE,
                BookingResourceRoleScheme.EDIT_CONFIGURATION),
            List.of());
    when(accessManager.resolve(nullable(ResourceAccess.class), eq(actor))).thenReturn(ownerAccess);
    when(accessManager.resolveForMutation(nullable(ResourceAccess.class), eq(actor)))
        .thenReturn(ownerAccess);
    when(accessManager.resolveAll(any(), eq(actor)))
        .thenAnswer(
            invocation -> {
              List<ResourceAccess> accesses = invocation.getArgument(0);
              Map<Long, ResolvedResourceAccess> result = new HashMap<>();
              accesses.forEach(access -> result.put(access.getId(), ownerAccess));
              return result;
            });
  }

  static ResolvedBookableTarget target(long id) {
    Instrument instrument = instrument(id, null);
    return new ResolvedBookableTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, id), instrument);
  }

  static Instrument instrument(long id, User owner) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    instrument.setOwner(owner);
    return instrument;
  }

  BookingConfiguration configuration(long id, long targetId) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(id);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, targetId));
    ResourceAccess access =
        new ResourceAccess(BookingResourceRoleScheme.SCHEME_KEY, null, new Date());
    access.setId(targetId);
    access.addAssignment(ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, actor));
    configuration.setResourceAccess(access);
    return configuration;
  }

  static BookingConfigurationDefaults defaults(
      long granularity,
      String start,
      String end,
      long before,
      long after,
      long maximumDuration,
      boolean doubleBooking) {
    BookingConfigurationDefaults defaults = new BookingConfigurationDefaults();
    defaults.setId(BookingConfigurationDefaults.SINGLETON_ID);
    defaults.setSlotGranularityMinutes(granularity);
    defaults.setOpeningHours(hours(start, end));
    defaults.setBufferBeforeMinutes(before);
    defaults.setBufferAfterMinutes(after);
    defaults.setMaxBookingDurationMinutes(maximumDuration);
    defaults.setAllowDoubleBooking(doubleBooking);
    return defaults;
  }
}
