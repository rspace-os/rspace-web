package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingLocationQuery;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2BookingLocationResource;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
import com.researchspace.service.inventory.InstrumentReadAccess;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Booking location filter rewrite")
class BookingLocationFilterManagerImplTest {

  private final InstrumentDao instruments = mock(InstrumentDao.class);
  private final InstrumentReadAccess instrumentReadAccess = mock(InstrumentReadAccess.class);
  private final BookingLocationQuery locationQuery = mock(BookingLocationQuery.class);
  private final BookingLocationFilterManagerImpl manager =
      new BookingLocationFilterManagerImpl(instruments, instrumentReadAccess, locationQuery);
  private final User caller = new User("reader");

  @Test
  void replacesLocationIdentityWithTheReadableConfiguredItemsInPlace() {
    when(instruments.findConfiguredInstrumentIdsInReadableParents(Set.of(12L), caller))
        .thenReturn(Set.of(5L, 3L));
    FilterExpression enabled = comparison("enabled", Operator.EQUAL, true);
    FilterExpression filter =
        new FilterExpression.Or(
            List.of(enabled, comparison("location", Operator.EQUAL, reference(12L))));

    assertEquals(
        new FilterExpression.Or(
            List.of(
                enabled,
                new FilterExpression.Comparison(
                    "target.value", Operator.IN, List.of(3L, 5L), false))),
        manager.resolveLocations(filter, caller));
  }

  @Test
  void mapsEveryOperatorAndTheIdSelector() {
    when(instruments.findConfiguredInstrumentIdsInReadableParents(Set.of(12L, 7L), caller))
        .thenReturn(Set.of(5L));
    when(instruments.findConfiguredInstrumentIdsInReadableParents(null, caller))
        .thenReturn(Set.of(5L, 6L));

    assertEquals(
        targets(Operator.NOT_IN, 5L),
        manager.resolveLocations(
            new FilterExpression.Comparison(
                "location", Operator.NOT_IN, List.of(reference(12L), reference(7L)), false),
            caller));
    assertEquals(
        targets(Operator.IN, 5L),
        manager.resolveLocations(
            new FilterExpression.Comparison("location.value", Operator.IN, List.of(12L, 7L), false),
            caller));
    assertEquals(
        targets(Operator.IN, 5L, 6L),
        manager.resolveLocations(comparison("location", Operator.EXISTS, true), caller));
    assertEquals(
        targets(Operator.NOT_IN, 5L, 6L),
        manager.resolveLocations(comparison("location", Operator.EXISTS, false), caller));
  }

  @Test
  void anUnreadableOrMissingLocationContributesNoItems() {
    when(instruments.findConfiguredInstrumentIdsInReadableParents(Set.of(99L), caller))
        .thenReturn(Set.of());

    assertEquals(
        ApiV2BookingLocationResource.noTarget(),
        manager.resolveLocations(comparison("location", Operator.EQUAL, reference(99L)), caller));
    assertEquals(
        targets(Operator.NOT_IN, -1L),
        manager.resolveLocations(
            comparison("location", Operator.NOT_EQUAL, reference(99L)), caller));
  }

  @Test
  void queriesInventoryOncePerDistinctLocationSetAndNeverWithoutALocation() {
    when(instruments.findConfiguredInstrumentIdsInReadableParents(Set.of(12L), caller))
        .thenReturn(Set.of(5L));
    FilterExpression location = comparison("location", Operator.EQUAL, reference(12L));

    manager.resolveLocations(new FilterExpression.And(List.of(location, location)), caller);
    FilterExpression unrelated = comparison("enabled", Operator.EQUAL, true);

    assertSame(unrelated, manager.resolveLocations(unrelated, caller));
    verify(instruments, times(1)).findConfiguredInstrumentIdsInReadableParents(Set.of(12L), caller);
  }

  @Test
  void answersEveryTopLevelLocationRuleWithAReadableParentRestrictionInsteadOfAnItemList() {
    String path = "booking.bookingConfiguration.target";
    String names = "booking_bookingConfiguration_target_location";
    FilterExpression readConstraint = comparison("readConstraint", Operator.EQUAL, true);
    FilterExpression editConstraint = comparison("editConstraint", Operator.EQUAL, true);
    RsqlCollectionQuery.Predicate inTwelve = predicate("EXISTS inTwelve");
    RsqlCollectionQuery.Predicate notInSeven = predicate("EXISTS notInSeven");
    RsqlCollectionQuery.Predicate hasParent = predicate("EXISTS hasParent");
    RsqlCollectionQuery.Predicate lacksParent = predicate("EXISTS lacksParent");
    when(instrumentReadAccess.constraint(caller, false)).thenReturn(readConstraint);
    when(instrumentReadAccess.constraint(caller, true)).thenReturn(editConstraint);
    when(locationQuery.readableParent(
            caller, path, Set.of(12L), true, names + 0, readConstraint, editConstraint))
        .thenReturn(inTwelve);
    when(locationQuery.readableParent(
            caller, path, Set.of(7L), false, names + 1, readConstraint, editConstraint))
        .thenReturn(notInSeven);
    when(locationQuery.readableParent(
            caller, path, null, true, names + 2, readConstraint, editConstraint))
        .thenReturn(hasParent);
    when(locationQuery.readableParent(
            caller, path, null, false, names + 3, readConstraint, editConstraint))
        .thenReturn(lacksParent);
    FilterExpression enabled = comparison("enabled", Operator.EQUAL, true);

    BookingLocationFilterManager.Resolved resolved =
        manager.resolveLocations(
            ResourceRequest.unpaged(
                new FilterExpression.And(
                    List.of(
                        comparison("location", Operator.EQUAL, reference(12L)),
                        new FilterExpression.Comparison(
                            "location.value", Operator.NOT_IN, List.of(7L), false),
                        comparison("location", Operator.EXISTS, true),
                        new FilterExpression.And(
                            List.of(enabled, comparison("location", Operator.EXISTS, false)))))),
            caller,
            path);

    assertEquals(enabled, resolved.request().filter());
    assertEquals(
        "(((EXISTS inTwelve) AND (EXISTS notInSeven)) AND (EXISTS hasParent)) AND (EXISTS"
            + " lacksParent)",
        resolved.restriction().expression());
    verify(instruments, never()).findConfiguredInstrumentIdsInReadableParents(any(), any());
  }

  @Test
  void doesNotResolveInventoryConstraintsForDisabledOrLockedCallers() {
    User disabled = new User("disabled");
    disabled.setEnabled(false);
    User locked = new User("locked");
    locked.setAccountLocked(true);
    String path = "booking.bookingConfiguration.target";
    FilterExpression location = comparison("location", Operator.EQUAL, reference(12L));
    BookingLocationFilterManagerImpl realQueryManager =
        new BookingLocationFilterManagerImpl(
            instruments, instrumentReadAccess, new BookingLocationQuery());

    for (User deniedCaller : new User[] {null, disabled, locked}) {
      assertEquals(
          "1 = 0",
          realQueryManager
              .resolveLocations(ResourceRequest.unpaged(location), deniedCaller, path)
              .restriction()
              .expression());
    }
    org.mockito.Mockito.verifyNoInteractions(instrumentReadAccess);
  }

  @Test
  void leavesARequestWithoutLocationUntouched() {
    ResourceRequest request = ResourceRequest.unpaged(comparison("enabled", Operator.EQUAL, true));

    BookingLocationFilterManager.Resolved resolved =
        manager.resolveLocations(request, caller, "bookingConfiguration.target");

    assertSame(request, resolved.request());
    assertNull(resolved.restriction());
  }

  @Test
  void stillListsItemsForAnExistsInsideADisjunction() {
    when(instruments.findConfiguredInstrumentIdsInReadableParents(null, caller))
        .thenReturn(Set.of(5L));
    FilterExpression enabled = comparison("enabled", Operator.EQUAL, true);

    BookingLocationFilterManager.Resolved resolved =
        manager.resolveLocations(
            ResourceRequest.unpaged(
                new FilterExpression.Or(
                    List.of(enabled, comparison("location", Operator.EXISTS, true)))),
            caller,
            "bookingConfiguration.target");

    assertNull(resolved.restriction());
    assertEquals(
        new FilterExpression.Or(List.of(enabled, targets(Operator.IN, 5L))),
        resolved.request().filter());
  }

  @Test
  void refusesALocationThatNamesMoreItemsThanOneQueryMayBind() {
    Set<Long> many =
        LongStream.rangeClosed(1, BookingLocationFilterManagerImpl.MAX_LOCATION_ITEMS + 1)
            .boxed()
            .collect(Collectors.toSet());
    when(instruments.findConfiguredInstrumentIdsInReadableParents(Set.of(12L), caller))
        .thenReturn(many);

    CollectionQueryException refused =
        assertThrows(
            CollectionQueryException.class,
            () ->
                manager.resolveLocations(
                    comparison("location", Operator.EQUAL, reference(12L)), caller));
    assertEquals(CollectionQueryException.Reason.COMPLEXITY, refused.getReason());
  }

  @Test
  void parsesContainerAndWorkbenchLocationsAsContainerIds() {
    FilterExpression parsed =
        new RsqlFilterParser(ApiV2BookingConfigurationResource.DESCRIPTION)
            .parse("location=in=(IC12,IC7)");

    assertEquals(
        new FilterExpression.Comparison(
            "location", Operator.IN, List.of(reference(12L), reference(7L)), false),
        parsed);
    assertThrows(
        RuntimeException.class,
        () ->
            new RsqlFilterParser(ApiV2BookingConfigurationResource.DESCRIPTION)
                .parse("location==BE7"));
  }

  @Test
  void restoresOnlyReadableLocationsKeepingTheWorkbenchGlobalId() {
    when(instruments.getBookingCatalogueLocations(null, Set.of(7L, 12L), 1, 2, caller))
        .thenReturn(
            new ResourcePage<>(
                List.of(
                    new InstrumentParentLocationSummary(
                        7L, "WB reader", Container.ContainerType.WORKBENCH)),
                1));

    assertEquals(
        java.util.Map.of(7L, new ApiV2BookingLocationResource.Location(7L, "BE7", "WB reader")),
        manager.findReadableLocations(Set.of(7L, 12L), caller));
  }

  private static RsqlCollectionQuery.Predicate predicate(String expression) {
    return new RsqlCollectionQuery.Predicate(expression, Map.of());
  }

  private static ResourceReference<String, Long> reference(long id) {
    return new ResourceReference<>(ApiV2BookingLocationResource.RESOURCE_NAME, id);
  }

  private static FilterExpression targets(Operator operator, Long... ids) {
    return new FilterExpression.Comparison(
        "target.value", operator, List.<Object>of((Object[]) ids), false);
  }

  private static FilterExpression.Comparison comparison(
      String field, Operator operator, Object value) {
    return new FilterExpression.Comparison(field, operator, List.of(value), false);
  }
}
