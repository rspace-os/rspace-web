package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.BookingResourceRoleScheme;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.resourceaccess.ResourceRoleReadAccess;
import com.researchspace.service.resourceaccess.ResourceRoleSchemeRegistry;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Covers filtering booking configurations by a field of their target instrument, which only a real
 * database can check: Blaze must accept the correlated subquery, and the target's own read rule
 * must narrow it.
 */
@ExtendWith(SpringExtension.class)
class BookingConfigurationTargetFilterIT extends SpringTransactionalTest {

  private static final String PROTECTED_CONFIGURATION_RESOURCE =
      "role-protected-booking-configurations";
  private static final FilterExpression NO_INSTRUMENT_IS_READABLE =
      new FilterExpression.Comparison("name", Operator.EQUAL, List.of("nothing"), false);

  private static final CollectionDescription<BookingConfiguration> PROTECTED_CONFIGURATIONS =
      bookingConfigurations(
          PROTECTED_CONFIGURATION_RESOURCE,
          new ResourceRoleReadAccess(
              new ResourceRoleSchemeRegistry(List.of(new BookingResourceRoleScheme())),
              BookingResourceRoleScheme.SCHEME_KEY,
              "resourceAccess.id"));
  private static final CollectionDescription<TimeSlotBooking> TEST_BOOKINGS =
      new CollectionDescription<>(
          "test-bookings",
          TimeSlotBooking.class,
          List.of(
              Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), TimeSlotBooking::getId),
              Relationship.referenceToOne(
                  "configuration",
                  PROTECTED_CONFIGURATION_RESOURCE,
                  CollectionFieldTypes.longNumber(),
                  BookingConfiguration.class,
                  TimeSlotBooking::getBookingConfiguration,
                  BookingConfiguration::getId,
                  "bookingConfiguration.id")),
          "id",
          List.of(new Sort("id", true)));
  private static final ResourceRegistry ROLE_PROTECTED_REGISTRY =
      new ResourceRegistry(List.of(TEST_BOOKINGS, PROTECTED_CONFIGURATIONS));
  private static final CollectionQueryExecutor<TimeSlotBooking> TEST_BOOKING_QUERY =
      new CollectionQueryExecutor<>(TimeSlotBooking.class, TEST_BOOKINGS, "booking");
  private static final ResolvedRuntimeField CONFIGURATION_TIME_ZONE =
      new ResolvedRuntimeField(
          new RuntimeFieldDefinition(
              "timezone",
              "runtime.timezone",
              "Timezone",
              RuntimeFieldValueType.TEXT,
              "test",
              "Test",
              List.of()),
          new RuntimeFieldBinding(BookingConfiguration.class, "id", "timeZone", Map.of()));

  private final InstrumentDao instrumentDao;
  private final BookingConfigurationDao bookingConfigurationDao;
  private final CriteriaBuilderFactory criteriaBuilderFactory;
  private final InstrumentCustomFieldManager customFields;
  private final InstrumentEntityApiManager instrumentApiManager;

  @Autowired
  BookingConfigurationTargetFilterIT(
      InstrumentDao instrumentDao,
      BookingConfigurationDao bookingConfigurationDao,
      CriteriaBuilderFactory criteriaBuilderFactory,
      InstrumentCustomFieldManager customFields,
      InstrumentEntityApiManager instrumentApiManager) {
    this.instrumentDao = instrumentDao;
    this.bookingConfigurationDao = bookingConfigurationDao;
    this.criteriaBuilderFactory = criteriaBuilderFactory;
    this.customFields = customFields;
    this.instrumentApiManager = instrumentApiManager;
  }

  private static final CollectionQueryExecutor<BookingConfiguration> CONFIGURATIONS =
      new CollectionQueryExecutor<>(
          BookingConfiguration.class,
          ApiV2BookingConfigurationResource.DESCRIPTION,
          "bookingConfiguration");

  private static RelationshipReadAccess targets(FilterExpression access) {
    return RelationshipReadAccess.forActor(registry(access), null);
  }

  private static ResourceRegistry registry(FilterExpression access) {
    AccessFunction readAccess =
        AccessFunction.documented(
            "Test relationship target access.",
            Set.of(),
            ignored -> access == null ? AccessResult.allowed() : AccessResult.allowedWhere(access));
    var bookingTarget =
        new CollectionDescription<>(
            ApiV2InstrumentResource.RESOURCE_NAME,
            Instrument.class,
            ApiV2InstrumentResource.DESCRIPTION.fields(),
            ApiV2InstrumentResource.DESCRIPTION.idField(),
            ApiV2InstrumentResource.DESCRIPTION.defaultSort(),
            AccessPolicy.readOnly(readAccess));
    return new ResourceRegistry(
        List.of(
            ApiV2BookingConfigurationResource.DESCRIPTION,
            ApiV2UserResource.DESCRIPTION,
            bookingTarget));
  }

  private static CollectionDescription<BookingConfiguration> bookingConfigurations(
      String resourceName, AccessFunction readAccess) {
    return new CollectionDescription<>(
        resourceName,
        BookingConfiguration.class,
        ApiV2BookingConfigurationResource.DESCRIPTION.fields(),
        ApiV2BookingConfigurationResource.DESCRIPTION.idField(),
        ApiV2BookingConfigurationResource.DESCRIPTION.defaultSort(),
        AccessPolicy.readOnly(readAccess));
  }

  private static ResourceRequest byConfigurationTimeZone(Long bookingId) {
    String selector = "configuration." + CONFIGURATION_TIME_ZONE.selector();
    return new ResourceRequest(
        new FilterExpression.And(
            List.of(
                new FilterExpression.Comparison("id", Operator.EQUAL, List.of(bookingId), false),
                new FilterExpression.Comparison(selector, Operator.EQUAL, List.of("UTC"), false))),
        null,
        List.of(new Sort("id", true)),
        new ResourceRequest.Page(1, 20),
        ResourceFieldSelections.root(FieldSelection.all()),
        IncludeTree.empty(),
        new RuntimeFieldSelection(
            Map.of(selector, CONFIGURATION_TIME_ZONE),
            Set.of(),
            Map.of(selector, "configuration")));
  }

  private static ResourceRequest narrowedByTargetName(String term, List<Long> instrumentIds) {
    return targetNameRequest(
        new FilterExpression.Comparison("target.name", Operator.LIKE, List.of(term), false),
        instrumentIds);
  }

  private static ResourceRequest byTargetName(String term) {
    return targetNameRequest(
        new FilterExpression.Comparison("target.name", Operator.CONTAINS, List.of(term), false),
        null);
  }

  private static ResourceRequest targetNameRequest(
      FilterExpression nameFilter, List<Long> instrumentIds) {
    FilterExpression filter = nameFilter;
    if (instrumentIds != null) {
      filter =
          new FilterExpression.And(
              List.of(
                  new FilterExpression.Comparison(
                      "target.value", Operator.IN, List.copyOf(instrumentIds), false),
                  nameFilter));
    }
    return new ResourceRequest(
        filter,
        List.of(new Sort("id", true)),
        new ResourceRequest.Page(1, 20),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  private static ResourceRequest byTargetCustomField(ResolvedRuntimeField field, String value) {
    String selector = "target." + field.selector();
    return new ResourceRequest(
        new FilterExpression.Comparison(selector, Operator.EQUAL, List.of(value), false),
        null,
        List.of(new Sort("id", true)),
        new ResourceRequest.Page(1, 20),
        ResourceFieldSelections.root(FieldSelection.all()),
        IncludeTree.empty(),
        new RuntimeFieldSelection(Map.of(selector, field), Set.of(), Map.of(selector, "target")));
  }

  private BookingConfiguration configurationFor(Long instrumentId, User owner) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId));
    ResourceAccess access =
        new ResourceAccess(BookingResourceRoleScheme.SCHEME_KEY, owner, new Date());
    access.addAssignment(ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, owner));
    configuration.setResourceAccess(access);
    sessionFactory.getCurrentSession().persist(configuration);
    sessionFactory.getCurrentSession().flush();
    return configuration;
  }

  private TimeSlotBooking bookingFor(BookingConfiguration configuration, User requester) {
    Date start = new Date();
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setBookingConfiguration(configuration);
    booking.setRequester(requester);
    booking.setStartTime(start);
    booking.setEndTime(new Date(start.getTime() + 3_600_000));
    booking.setState(BookingState.CONFIRMED);
    booking.setCreatedBy(requester);
    booking.setUpdatedBy(requester);
    sessionFactory.getCurrentSession().persist(booking);
    sessionFactory.getCurrentSession().flush();
    return booking;
  }

  private void assertBookings(ResourceRequest request, User actor, List<Long> expectedIds) {
    RelationshipReadAccess access = RelationshipReadAccess.forActor(ROLE_PROTECTED_REGISTRY, actor);
    assertEquals(
        (long) expectedIds.size(),
        TEST_BOOKING_QUERY.count(
            criteriaBuilderFactory, sessionFactory.getCurrentSession(), request, null, access));
    assertEquals(
        expectedIds,
        TEST_BOOKING_QUERY
            .page(criteriaBuilderFactory, sessionFactory.getCurrentSession(), request, null, access)
            .resources()
            .stream()
            .map(TimeSlotBooking::getId)
            .toList());
  }

  private long count(ResourceRequest request, RelationshipReadAccess targets) {
    return CONFIGURATIONS.count(
        criteriaBuilderFactory, sessionFactory.getCurrentSession(), request, null, targets);
  }

  @Test
  void filtersByTheNameOfTheTargetInstrument() {
    User owner = createInitAndLoginAnyUser();
    String marker = UUID.randomUUID().toString();
    configurationFor(createBasicInstrumentForUser(owner, "Confocal " + marker).getId(), owner);
    configurationFor(createBasicInstrumentForUser(owner, "Centrifuge").getId(), owner);

    assertEquals(
        1,
        CONFIGURATIONS
            .page(
                criteriaBuilderFactory,
                sessionFactory.getCurrentSession(),
                byTargetName(marker),
                null,
                targets(null))
            .resources()
            .size());
    assertEquals(1, count(byTargetName(marker), targets(null)));
  }

  @Test
  void relationshipRuntimeFieldEnforcesNestedResourceRoleMembership() {
    User assigned = createInitAndLoginAnyUser();
    User unassigned = createInitAndLoginAnyUser();
    BookingConfiguration configuration =
        configurationFor(
            createBasicInstrumentForUser(assigned, "Role protected").getId(), assigned);
    TimeSlotBooking booking = bookingFor(configuration, assigned);
    ResourceRequest request = byConfigurationTimeZone(booking.getId());

    assertBookings(request, assigned, List.of(booking.getId()));
    assertBookings(request, unassigned, List.of());
  }

  @Test
  void appliesTheReadRuleOfTheTargetInsideTheSubquery() {
    User owner = createInitAndLoginAnyUser();
    String marker = UUID.randomUUID().toString();
    configurationFor(createBasicInstrumentForUser(owner, "Confocal " + marker).getId(), owner);
    FilterExpression noInstrumentIsReadable = NO_INSTRUMENT_IS_READABLE;

    assertEquals(1, count(byTargetName(marker), targets(null)));
    assertEquals(0, count(byTargetName(marker), targets(noInstrumentIsReadable)));
  }

  @Test
  void filtersByACustomFieldOfTheTargetInstrument() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner, "Cell line");
    Instrument matching =
        InstrumentCustomFieldTestUtils.instrumentFromTemplate(
            instrumentDao, instrumentApiManager, owner, template, "Readable scope", "BSL-2");
    Instrument other =
        InstrumentCustomFieldTestUtils.instrumentFromTemplate(
            instrumentDao, instrumentApiManager, owner, template, "Other scope", "BSL-1");
    configurationFor(matching.getId(), owner);
    configurationFor(other.getId(), owner);
    ResolvedRuntimeField field =
        InstrumentCustomFieldTestUtils.definitionOf(customFields, owner, template);

    assertEquals(1, count(byTargetCustomField(field, "BSL-2"), targets(null)));
    assertEquals(
        1,
        CONFIGURATIONS
            .page(
                criteriaBuilderFactory,
                sessionFactory.getCurrentSession(),
                byTargetCustomField(field, "BSL-2"),
                null,
                targets(null))
            .resources()
            .size());
    FilterExpression noInstrumentIsReadable = NO_INSTRUMENT_IS_READABLE;
    assertEquals(0, count(byTargetCustomField(field, "BSL-2"), targets(noInstrumentIsReadable)));
    assertEquals(0, count(byTargetCustomField(field, "BSL-9"), targets(null)));
  }

  @Test
  void targetAccessOnlyRestrictsQueriesThatObserveTheRelationship() {
    User owner = createInitAndLoginAnyUser();
    String marker = UUID.randomUUID().toString();
    BookingConfiguration configuration =
        configurationFor(createBasicInstrumentForUser(owner, "Private " + marker).getId(), owner);
    ResourceRequest targetFilter = byTargetName(marker);
    ResourceRequest configurationFilter =
        ResourceRequest.unpaged(
            new FilterExpression.Comparison(
                "id", Operator.EQUAL, List.of(configuration.getId()), false));
    RelationshipReadAccess readable = targets(null);
    RelationshipReadAccess unreadable =
        targets(new FilterExpression.Comparison("name", Operator.EQUAL, List.of("nothing"), false));

    assertEquals(1, bookingConfigurationDao.countResources(targetFilter, readable));
    assertEquals(0, bookingConfigurationDao.countResources(targetFilter, unreadable));
    assertEquals(0, bookingConfigurationDao.getResources(targetFilter, unreadable).total());
    assertEquals(1, bookingConfigurationDao.countResources(configurationFilter, unreadable));
    assertEquals(1, bookingConfigurationDao.getResources(configurationFilter, unreadable).total());
  }

  @Test
  void narrowingByTargetIdsPrunesWithoutLosingTheTargetReadRule() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument confocal = createBasicInstrumentForUser(owner, "Confocal microscope");
    ApiInstrument centrifuge = createBasicInstrumentForUser(owner, "Centrifuge");
    configurationFor(confocal.getId(), owner);
    configurationFor(centrifuge.getId(), owner);
    FilterExpression noInstrumentIsReadable = NO_INSTRUMENT_IS_READABLE;

    assertEquals(
        1, count(narrowedByTargetName("confocal", List.of(confocal.getId())), targets(null)));
    assertEquals(
        0, count(narrowedByTargetName("confocal", List.of(centrifuge.getId())), targets(null)));
    assertEquals(
        1,
        count(
            narrowedByTargetName("confocal", List.of(confocal.getId(), centrifuge.getId())),
            targets(null)));
    assertEquals(
        0,
        count(
            narrowedByTargetName("confocal", List.of(confocal.getId())),
            targets(noInstrumentIsReadable)));
  }
}
