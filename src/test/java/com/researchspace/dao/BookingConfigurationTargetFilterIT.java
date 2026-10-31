package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.booking.dao.BookingCalendarQuery;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Covers filtering booking configurations by a field of their target instrument, which only a real
 * database can check: Blaze must accept the correlated subquery, and the target's own read rule
 * must narrow it.
 */
@ExtendWith(SpringExtension.class)
class BookingConfigurationTargetFilterIT extends SpringTransactionalTest {

  private static final FilterExpression NO_INSTRUMENT_IS_READABLE =
      new FilterExpression.Comparison("name", Operator.EQUAL, List.of("nothing"), false);

  private final InstrumentDao instrumentDao;
  private final BookingConfigurationDao bookingConfigurationDao;
  private final CriteriaBuilderFactory criteriaBuilderFactory;
  private final InstrumentCustomFieldManager customFields;
  private final InstrumentEntityApiManager instrumentApiManager;

  private final ApiV2ResourceCatalog catalog;
  private final BookingCalendarQuery calendarQuery;
  private final TimeSlotBookingDao calendarBookings;

  @Autowired
  BookingConfigurationTargetFilterIT(
      InstrumentDao instrumentDao,
      BookingConfigurationDao bookingConfigurationDao,
      CriteriaBuilderFactory criteriaBuilderFactory,
      InstrumentCustomFieldManager customFields,
      InstrumentEntityApiManager instrumentApiManager,
      ApiV2ResourceCatalog catalog,
      BookingCalendarQuery calendarQuery,
      TimeSlotBookingDao calendarBookings) {
    this.instrumentDao = instrumentDao;
    this.bookingConfigurationDao = bookingConfigurationDao;
    this.criteriaBuilderFactory = criteriaBuilderFactory;
    this.customFields = customFields;
    this.instrumentApiManager = instrumentApiManager;
    this.catalog = catalog;
    this.calendarQuery = calendarQuery;
    this.calendarBookings = calendarBookings;
  }

  private static final Instant CALENDAR_START = Instant.parse("2026-09-20T00:00:00Z");
  private static final Instant CALENDAR_END = CALENDAR_START.plusSeconds(86400);

  @Test
  void calendarRequiresOneVisibleEventToMatchTheWholeExpressionAndCountsEachResourceOnce() {
    User owner = createInitAndLoginAnyUser();
    BookingConfiguration config =
        configurationFor(createBasicInstrumentForUser(owner, "Calendar scope").getId(), owner);
    calendarEvent(config, owner, "other", BookingEventKind.BOOKING, 3600, false);
    calendarEvent(config, owner, "needle", BookingEventKind.MAINTENANCE, 7200, false);
    String eventWhere = "kind==BOOKING";
    assertEquals(0, calendarPage(config, owner, eventWhere, "needle").total());
    calendarEvent(config, owner, "needle", BookingEventKind.BOOKING, 10800, false);
    calendarEvent(config, owner, "needle", BookingEventKind.BOOKING, 14400, false);
    var page = calendarPage(config, owner, eventWhere, "needle");
    assertEquals(1, page.total());
    assertEquals(
        List.of(config.getId()),
        page.resources().stream().map(BookingConfiguration::getId).toList());
    User stranger = createInitAndLoginAnyUser();
    assertEquals(0, calendarPage(config, stranger, eventWhere, "needle").total());
    var events = catalog.find("bookings").orElseThrow();
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2ResourceRequestParser.filtered(
                "target==IN" + config.getTarget().id() + ";privacy==busy",
                events.description(),
                catalog.registry()));
  }

  @Test
  void calendarSearchCombinesTheSameEventWithItsItemAndPreservesEmptyItemMatches() {
    User owner = createInitAndLoginAnyUser();
    BookingConfiguration config =
        configurationFor(createBasicInstrumentForUser(owner, "needle instrument").getId(), owner);
    assertEquals(1, calendarPage(config, owner, null, "needle").total());
    assertEquals(0, calendarPage(config, owner, null, owner.getUsername()).total());
    assertEquals(0, calendarPage(config, owner, "kind==BOOKING", "needle").total());
    calendarEvent(config, owner, "other", BookingEventKind.BOOKING, 3600, false);
    assertEquals(1, calendarPage(config, owner, "kind==BOOKING", "needle").total());
    assertEquals(1, calendarPage(config, owner, null, owner.getUsername()).total());
    calendarEvent(config, owner, "purpose-only", BookingEventKind.MAINTENANCE, 7200, false);
    assertEquals(1, calendarPage(config, owner, null, "purpose-only").total());
    assertEquals(0, calendarPage(config, owner, "kind==BOOKING", "purpose-only").total());
    assertEquals(1, calendarPage(config, owner, "kind==MAINTENANCE", "purpose-only").total());
  }

  @Test
  void calendarIgnoresDeletedAndOutOfWindowEventsAndGuardsDescriptionSearch() {
    User owner = createInitAndLoginAnyUser();
    var item = createBasicInstrumentForUser(owner, "Calendar scope");
    Instrument instrument = instrumentDao.get(item.getId());
    instrument.setDescription("description-only");
    BookingConfiguration config = configurationFor(item.getId(), owner);
    calendarEvent(config, owner, "deleted", BookingEventKind.BOOKING, 3600, true);
    calendarEvent(config, owner, "tomorrow", BookingEventKind.BOOKING, 90000, false);
    assertEquals(0, calendarPage(config, owner, "kind==BOOKING", null).total());
    assertEquals(1, calendarPage(config, owner, null, "description-only").total());
    User inventoryOwner = createInitAndLoginAnyUser();
    instrument.setOwner(inventoryOwner);
    instrument.setSharingMode(InventorySharingMode.OWNER_ONLY);
    sessionFactory.getCurrentSession().flush();
    assertEquals(0, calendarPage(config, owner, null, "description-only").total());
    assertEquals(0, calendarPage(config, owner, null, "Calendar scope").total());
    assertEquals(0, calendarPage(config, owner, null, "IN" + item.getId()).total());
  }

  private ResourcePage<BookingConfiguration> calendarPage(
      BookingConfiguration config, User caller, String eventWhere, String text) {
    var events = catalog.find("bookings").orElseThrow();
    ResourceRequest eventRequest =
        ApiV2ResourceRequestParser.filtered(eventWhere, events.description(), catalog.registry());
    ResourceRequest request =
        ResourceRequest.unpaged(
            new FilterExpression.Comparison("id", Operator.EQUAL, List.of(config.getId()), false));
    return bookingConfigurationDao.getCalendarResources(
        request,
        RelationshipReadAccess.forActor(catalog.registry(), caller),
        calendarQuery.resources(eventRequest, CALENDAR_START, CALENDAR_END, text, caller));
  }

  private void calendarEvent(
      BookingConfiguration config,
      User user,
      String purpose,
      BookingEventKind kind,
      int offset,
      boolean deleted) {
    var event = new TimeSlotBooking();
    event.setBookingConfiguration(config);
    event.setRequester(user);
    event.setStartTime(Date.from(CALENDAR_START.plusSeconds(offset)));
    event.setEndTime(Date.from(CALENDAR_START.plusSeconds(offset + 1800)));
    event.setKind(kind);
    event.setPurpose(purpose);
    event.setState(BookingState.CONFIRMED);
    event.setDeleted(deleted);
    event.setCreatedBy(user);
    event.setUpdatedBy(user);
    sessionFactory.getCurrentSession().persist(event);
    sessionFactory.getCurrentSession().flush();
  }

  @ParameterizedTest
  @CsvSource({
    "false, ==BSL-2",
    "false, !=BSL-1",
    "false, =exists=false",
    "true, ==BSL-2",
    "true, !=BSL-1",
    "true, =exists=false"
  })
  void productionRuntimeFiltersPreserveSafeBookingReferencesButRestrictInventoryValues(
      boolean extra, String predicate) {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner, "Safety level");
    String value = predicate.equals("=exists=false") ? "" : "BSL-2";
    Instrument readable =
        InstrumentCustomFieldTestUtils.instrumentFromTemplate(
            instrumentDao, instrumentApiManager, owner, template, "Readable", value);
    Instrument privateItem =
        InstrumentCustomFieldTestUtils.instrumentFromTemplate(
            instrumentDao, instrumentApiManager, owner, template, "Private", value);
    String extraName = "Safety " + UUID.randomUUID();
    if (extra) {
      for (Instrument instrument : List.of(readable, privateItem)) {
        var extraField =
            recordFactory.createExtraField(extraName, FieldType.TEXT, owner, instrument);
        extraField.setData(value);
        instrument.addExtraField(extraField);
        instrumentDao.save(instrument);
      }
    }
    BookingConfiguration visible = configurationFor(readable.getId(), owner);
    BookingConfiguration hiddenValues = configurationFor(privateItem.getId(), owner);
    User stranger = createInitAndLoginAnyUser();
    privateItem.setOwner(stranger);
    privateItem.setSharingMode(InventorySharingMode.OWNER_ONLY);
    instrumentDao.save(privateItem);
    sessionFactory.getCurrentSession().flush();
    String selector =
        extra
            ? catalog.runtimeFieldsOf(ApiV2InstrumentResource.RESOURCE_NAME).stream()
                .filter(provider -> provider.namespace().equals("extraFields"))
                .findFirst()
                .orElseThrow()
                .discover(owner, new RuntimeFieldCatalogQuery(extraName, Set.of(), 1, 50))
                .fields()
                .stream()
                .filter(field -> field.label().equals(extraName))
                .findFirst()
                .orElseThrow()
                .selector()
            : InstrumentCustomFieldTestUtils.definitionOf(customFields, owner, template).selector();
    var registration = catalog.find("booking-configurations").orElseThrow();
    var request =
        ApiV2ResourceRequestParser.filtered(
            "target." + selector + predicate,
            registration.description(),
            catalog.registry(),
            registration.runtimeFieldContext(owner));
    RelationshipReadAccess access = RelationshipReadAccess.forActor(catalog.registry(), owner);
    assertEquals(1, bookingConfigurationDao.countResources(request, access));
    assertEquals(
        List.of(visible.getId()),
        bookingConfigurationDao.getResources(request, access).resources().stream()
            .map(BookingConfiguration::getId)
            .toList());
    var identity =
        ApiV2ResourceRequestParser.filtered(
            "target==IN" + privateItem.getId(), registration.description(), catalog.registry());
    assertEquals(0, bookingConfigurationDao.countResources(identity, access));
    assertEquals(List.of(), bookingConfigurationDao.getResources(identity, access).resources());
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
            bookingTarget,
            com.researchspace.inventory.model.ApiV2ContainerResource.DESCRIPTION));
  }

  private static ResourceRequest byTargetNameAndIds(String term, List<Long> instrumentIds) {
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
    sessionFactory.getCurrentSession().persist(configuration);
    sessionFactory.getCurrentSession().flush();
    return configuration;
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
  void combinesTargetNameAndIdFiltersWithoutLosingTheTargetReadRule() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument confocal = createBasicInstrumentForUser(owner, "Confocal microscope");
    ApiInstrument centrifuge = createBasicInstrumentForUser(owner, "Centrifuge");
    configurationFor(confocal.getId(), owner);
    configurationFor(centrifuge.getId(), owner);
    FilterExpression noInstrumentIsReadable = NO_INSTRUMENT_IS_READABLE;

    assertEquals(
        1, count(byTargetNameAndIds("confocal", List.of(confocal.getId())), targets(null)));
    assertEquals(
        0, count(byTargetNameAndIds("confocal", List.of(centrifuge.getId())), targets(null)));
    assertEquals(
        1,
        count(
            byTargetNameAndIds("confocal", List.of(confocal.getId(), centrifuge.getId())),
            targets(null)));
    assertEquals(
        0,
        count(
            byTargetNameAndIds("confocal", List.of(confocal.getId())),
            targets(noInstrumentIsReadable)));
  }
}
