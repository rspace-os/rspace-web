package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.dao.BookingInstrumentQueryDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.FeatureFlagManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingCatalogueManagerImplTest {

  @Mock private BookingConfigurationManager configurations;
  @Mock private BookingConfigurationDao configurationDao;
  @Mock private InstrumentDao instruments;
  @Mock private BookingInstrumentQueryDao bookingInstruments;
  @Mock private FeatureFlagManager featureFlags;
  @Mock private User caller;

  private BookingCatalogueManagerImpl manager;

  @BeforeEach
  void setUp() {
    manager =
        new BookingCatalogueManagerImpl(
            configurations,
            configurationDao,
            instruments,
            bookingInstruments,
            featureFlags,
            new BookingResourceRoleScheme());
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, caller)).thenReturn(true);
    when(caller.hasSysadminRole()).thenReturn(false);
  }

  @Test
  void appliesCapabilityTargetIntersectionBeforeRequestedPage() {
    Set<Long> capableTargetIds = Set.of(101L, 202L);
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(7L);
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 101L));
    List<BookingOpeningHoursEntry> openingHours =
        List.of(
            new BookingOpeningHoursEntry(1, "09:00", "17:00"),
            new BookingOpeningHoursEntry(2, "10:00", "16:00"));
    configuration.setOpeningHours(openingHours);
    Instrument instrument = mock(Instrument.class);
    when(instrument.getId()).thenReturn(101L);
    when(instrument.getGlobalIdentifier()).thenReturn("IN101");
    when(instrument.getName()).thenReturn("Microscope");
    Set<String> bookingRoles =
        Set.of(
            BookingResourceRoleScheme.OWNER,
            BookingResourceRoleScheme.MANAGER,
            BookingResourceRoleScheme.BOOKER);
    when(configurationDao.findBookableInstrumentIds(caller, bookingRoles))
        .thenReturn(capableTargetIds);
    when(configurations.getConfigurations(any(ResourceRequest.class), same(caller)))
        .thenReturn(new ResourcePage<>(List.of(configuration), 2));
    when(bookingInstruments.getBookingRelationshipTargets(Set.of(101L)))
        .thenReturn(Map.of(101L, instrument));
    when(bookingInstruments.getReadableParentLocationSummaries(Set.of(101L), caller))
        .thenReturn(Map.of());

    BookingCatalogueManager.Page result =
        manager.search(
            null,
            null,
            null,
            List.of(),
            List.of(),
            BookingCatalogueManager.Capability.CREATE_BOOKING,
            3,
            7,
            caller);

    assertEquals(3, result.page());
    assertEquals(7, result.pageSize());
    assertEquals(2, result.total());
    assertEquals(openingHours, result.items().get(0).openingHours());
    ArgumentCaptor<ResourceRequest> request = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(configurations).getConfigurations(request.capture(), same(caller));
    assertEquals(new ResourceRequest.Page(3, 7), request.getValue().page());
    FilterExpression.And filter =
        assertInstanceOf(FilterExpression.And.class, request.getValue().filter());
    assertTrue(
        filter.children().stream()
            .filter(FilterExpression.Comparison.class::isInstance)
            .map(FilterExpression.Comparison.class::cast)
            .anyMatch(
                comparison ->
                    comparison.field().equals("target.value")
                        && comparison.operator() == Operator.IN
                        && Set.copyOf(comparison.values()).equals(capableTargetIds)));
  }

  @Test
  void returnsEmptyPageWithoutQueryWhenNonSysadminHasNoCapableTargets() {
    Set<String> bookingRoles =
        Set.of(
            BookingResourceRoleScheme.OWNER,
            BookingResourceRoleScheme.MANAGER,
            BookingResourceRoleScheme.BOOKER);
    when(configurationDao.findBookableInstrumentIds(caller, bookingRoles)).thenReturn(Set.of());

    BookingCatalogueManager.Page result =
        manager.search(
            null,
            null,
            null,
            List.of(),
            List.of(),
            BookingCatalogueManager.Capability.CREATE_BOOKING,
            4,
            9,
            caller);

    assertEquals(List.of(), result.items());
    assertEquals(4, result.page());
    assertEquals(9, result.pageSize());
    assertEquals(0, result.total());
    assertEquals(List.of(), result.facets().types());
    verify(configurations, never()).getConfigurations(any(ResourceRequest.class), eq(caller));
  }
}
