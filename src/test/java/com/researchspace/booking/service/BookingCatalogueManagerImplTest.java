package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingCalendarQuery;
import com.researchspace.booking.dao.BookingItemQuery;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.model.User;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.Sort;
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
  @Mock private InstrumentDao instruments;
  @Mock private FeatureFlagManager featureFlags;
  @Mock private BookingItemQuery itemQuery;
  @Mock private BookingCalendarQuery calendarQuery;
  @Mock private User caller;

  private BookingCatalogueManagerImpl manager;

  @BeforeEach
  void setUp() {
    manager =
        new BookingCatalogueManagerImpl(
            configurations, instruments, featureFlags, itemQuery, calendarQuery);
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, caller)).thenReturn(true);
  }

  @Test
  void appliesCapabilityTargetIntersectionBeforeRequestedPage() {
    RsqlCollectionQuery.Predicate accessRestriction =
        new RsqlCollectionQuery.Predicate(
            "EXISTS bookingItemCapability", Map.of("subject", caller));
    when(itemQuery.restriction(caller, true, false, "bookingConfiguration.target"))
        .thenReturn(accessRestriction);
    when(configurations.getConfigurations(
            any(ResourceRequest.class), same(caller), same(accessRestriction)))
        .thenReturn(new ResourcePage<>(List.of(), 2));
    when(instruments.getBookingRelationshipTargets(Set.of())).thenReturn(Map.of());
    when(instruments.getReadableParentLocationSummaries(Set.of(), caller)).thenReturn(Map.of());

    BookingCatalogueManager.Page result =
        manager.search(
            null,
            null,
            ResourceRequest.unpaged(null),
            List.of(),
            List.of(),
            BookingCatalogueManager.Capability.CREATE_BOOKING,
            3,
            7,
            caller);

    assertEquals(3, result.page());
    assertEquals(7, result.pageSize());
    assertEquals(2, result.total());
    ArgumentCaptor<ResourceRequest> request = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(configurations)
        .getConfigurations(request.capture(), same(caller), same(accessRestriction));
    assertEquals(new ResourceRequest.Page(3, 7), request.getValue().page());
    FilterExpression.And filter =
        assertInstanceOf(FilterExpression.And.class, request.getValue().serverConstraint());
    assertTrue(
        filter.children().stream()
            .filter(FilterExpression.Comparison.class::isInstance)
            .map(FilterExpression.Comparison.class::cast)
            .anyMatch(
                comparison ->
                    comparison.field().equals("enabled")
                        && comparison.operator() == Operator.EQUAL));
  }

  @Test
  void returnsEmptyPageWithoutQueryWhenNonSysadminHasNoCapableTargets() {
    RsqlCollectionQuery.Predicate noAccess = new RsqlCollectionQuery.Predicate("1 = 0", Map.of());
    when(itemQuery.restriction(caller, true, false, "bookingConfiguration.target"))
        .thenReturn(noAccess);
    when(configurations.getConfigurations(any(ResourceRequest.class), same(caller), same(noAccess)))
        .thenReturn(new ResourcePage<>(List.of(), 0));

    BookingCatalogueManager.Page result =
        manager.search(
            null,
            null,
            ResourceRequest.unpaged(null),
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
    verify(configurations)
        .getConfigurations(any(ResourceRequest.class), same(caller), same(noAccess));
  }

  @Test
  void preservesCallerFilterConstraintAndRuntimeWhileOwningCatalogueRequestShape() {
    FilterExpression callerFilter =
        new FilterExpression.Or(
            List.of(
                new FilterExpression.Comparison("id", Operator.EQUAL, List.of(7L), false),
                new FilterExpression.Comparison("id", Operator.EQUAL, List.of(8L), false)));
    FilterExpression callerConstraint =
        new FilterExpression.Comparison("id", Operator.GREATER_THAN, List.of(0L), false);
    RuntimeFieldSelection runtime =
        new RuntimeFieldSelection(
            Map.of("target.customFields.SF1", org.mockito.Mockito.mock(ResolvedRuntimeField.class)),
            Set.of());
    ResourceRequest request =
        new ResourceRequest(
            callerFilter,
            callerConstraint,
            List.of(new Sort("id", false)),
            new ResourceRequest.Page(8, 44),
            ResourceFieldSelections.root(FieldSelection.exclude(Set.of("id"))),
            new IncludeTree(Map.of("target", IncludeTree.empty())),
            runtime);
    when(configurations.getConfigurations(any(ResourceRequest.class), same(caller)))
        .thenReturn(new ResourcePage<>(List.of(), 0));
    when(instruments.getBookingRelationshipTargets(Set.of())).thenReturn(Map.of());
    when(instruments.getReadableParentLocationSummaries(Set.of(), caller)).thenReturn(Map.of());

    manager.search(null, null, request, List.of(), List.of(), null, 3, 7, caller);

    ArgumentCaptor<ResourceRequest> captured = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(configurations).getConfigurations(captured.capture(), same(caller));
    ResourceRequest catalogueRequest = captured.getValue();
    assertSame(callerFilter, catalogueRequest.filter());
    assertSame(runtime, catalogueRequest.runtime());
    assertEquals(new ResourceRequest.Page(3, 7), catalogueRequest.page());
    assertEquals(List.of(), catalogueRequest.sort());
    assertEquals(FieldSelection.all(), catalogueRequest.fields());
    assertEquals(IncludeTree.empty(), catalogueRequest.includes());

    QueryConstraint.And restrictions =
        assertInstanceOf(QueryConstraint.And.class, catalogueRequest.serverConstraint());
    assertSame(callerConstraint, restrictions.children().get(0));
    assertInstanceOf(FilterExpression.And.class, restrictions.children().get(1));
  }
}
