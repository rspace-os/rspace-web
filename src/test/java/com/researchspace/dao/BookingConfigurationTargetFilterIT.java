package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.BookingResourceRoleScheme;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
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
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
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

  @Autowired private InstrumentDao instrumentDao;
  @Autowired private BookingConfigurationDao bookingConfigurationDao;
  @Autowired private CriteriaBuilderFactory criteriaBuilderFactory;
  @Autowired private InstrumentCustomFieldManager customFields;
  @Autowired private InstrumentEntityApiManager instrumentApiManager;
  @Autowired private ApiV2ResourceCatalog catalog;

  @Autowired private com.researchspace.booking.dao.BookingCalendarQuery calendarQuery;
  @Autowired private com.researchspace.booking.dao.TimeSlotBookingDao calendarBookings;

  private static final java.time.Instant CALENDAR_START =
      java.time.Instant.parse("2026-09-20T00:00:00Z");
  private static final java.time.Instant CALENDAR_END = CALENDAR_START.plusSeconds(86400);

  @Test
  void calendarRequiresOneVisibleEventToMatchTheWholeExpressionAndCountsEachResourceOnce() {
    User owner = createInitAndLoginAnyUser();
    BookingConfiguration config =
        configurationFor(createBasicInstrumentForUser(owner, "Calendar scope").getId(), owner);
    calendarEvent(
        config,
        owner,
        "other",
        com.researchspace.model.booking.BookingEventKind.BOOKING,
        3600,
        false);
    calendarEvent(
        config,
        owner,
        "needle",
        com.researchspace.model.booking.BookingEventKind.MAINTENANCE,
        7200,
        false);
    String eventWhere = "kind==BOOKING";
    assertEquals(0, calendarPage(config, owner, eventWhere, "needle").total());
    calendarEvent(
        config,
        owner,
        "needle",
        com.researchspace.model.booking.BookingEventKind.BOOKING,
        10800,
        false);
    calendarEvent(
        config,
        owner,
        "needle",
        com.researchspace.model.booking.BookingEventKind.BOOKING,
        14400,
        false);
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
                "target==IN" + config.getTarget().id() + ";purpose==needle",
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
    calendarEvent(
        config,
        owner,
        "other",
        com.researchspace.model.booking.BookingEventKind.BOOKING,
        3600,
        false);
    assertEquals(1, calendarPage(config, owner, "kind==BOOKING", "needle").total());
    assertEquals(1, calendarPage(config, owner, null, owner.getUsername()).total());
    calendarEvent(
        config,
        owner,
        "purpose-only",
        com.researchspace.model.booking.BookingEventKind.MAINTENANCE,
        7200,
        false);
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
    calendarEvent(
        config,
        owner,
        "deleted",
        com.researchspace.model.booking.BookingEventKind.BOOKING,
        3600,
        true);
    calendarEvent(
        config,
        owner,
        "tomorrow",
        com.researchspace.model.booking.BookingEventKind.BOOKING,
        90000,
        false);
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

  private com.researchspace.model.collection.ResourcePage<BookingConfiguration> calendarPage(
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
      com.researchspace.model.booking.BookingEventKind kind,
      int offset,
      boolean deleted) {
    var event = new com.researchspace.model.booking.TimeSlotBooking();
    event.setBookingConfiguration(config);
    event.setRequester(user);
    event.setStartTime(Date.from(CALENDAR_START.plusSeconds(offset)));
    event.setEndTime(Date.from(CALENDAR_START.plusSeconds(offset + 1800)));
    event.setKind(kind);
    event.setPurpose(purpose);
    event.setState(com.researchspace.model.booking.BookingState.CONFIRMED);
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
    Instrument readable = instrumentFromTemplate(owner, template, "Readable", value);
    Instrument privateItem = instrumentFromTemplate(owner, template, "Private", value);
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
            ? catalog.runtimeFieldsOf("booking-instruments").stream()
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
            : definitionOf(owner, template).selector();
    var registration = catalog.find("booking-configurations").orElseThrow();
    var request =
        ApiV2ResourceRequestParser.filtered(
            "target." + selector + predicate,
            registration.description(),
            catalog.registry(),
            registration.runtimeFieldContext(owner, catalog::runtimeFieldsOf));
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
            ApiV2BookingInstrumentResource.RESOURCE_NAME,
            Instrument.class,
            ApiV2BookingInstrumentResource.DESCRIPTION.fields(),
            List.of(),
            ApiV2BookingInstrumentResource.DESCRIPTION.idField(),
            ApiV2BookingInstrumentResource.DESCRIPTION.defaultSort(),
            AccessPolicy.readOnly(readAccess));
    return new ResourceRegistry(
        List.of(
            ApiV2BookingConfigurationResource.DESCRIPTION,
            ApiV2UserResource.DESCRIPTION,
            bookingTarget));
  }

  private static ResourceRequest narrowedByTargetName(String term, List<Long> instrumentIds) {
    FilterExpression original =
        new FilterExpression.Comparison("target.name", Operator.LIKE, List.of(term), false);
    return new ResourceRequest(
        new FilterExpression.And(
            List.of(
                new FilterExpression.Comparison(
                    "target.value", Operator.IN, List.copyOf(instrumentIds), false),
                original)),
        List.of(new Sort("id", true)),
        new ResourceRequest.Page(1, 20),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  private static ResourceRequest byTargetName(String term) {
    return new ResourceRequest(
        new FilterExpression.Comparison("target.name", Operator.CONTAINS, List.of(term), false),
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

  private ResolvedRuntimeField definitionOf(User actor, ApiInstrumentTemplate template) {
    String templateId = String.valueOf(template.getId());
    return customFields
        .discover(actor, new RuntimeFieldCatalogQuery(null, Set.of(), 1, 50))
        .fields()
        .stream()
        .filter(field -> field.sourceId().endsWith(templateId))
        .findFirst()
        .flatMap(field -> customFields.resolve(field.selector(), actor))
        .orElseThrow(() -> new AssertionError("No readable definition for template " + templateId));
  }

  private Instrument instrumentFromTemplate(
      User owner, ApiInstrumentTemplate template, String name, String value) {
    ApiInstrument request = new ApiInstrument();
    request.setName(name);
    request.setTemplateId(template.getId());
    ApiInstrument created = instrumentApiManager.createNewApiInstrument(request, owner);
    Instrument instrument = instrumentDao.get(created.getId());
    instrument.getActiveFields().forEach(field -> field.setFieldData(value));
    instrumentDao.save(instrument);
    return instrument;
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
    FilterExpression noInstrumentIsReadable =
        new FilterExpression.Comparison("name", Operator.EQUAL, List.of("nothing"), false);

    assertEquals(1, count(byTargetName(marker), targets(null)));
    assertEquals(0, count(byTargetName(marker), targets(noInstrumentIsReadable)));
  }

  @Test
  void filtersByACustomFieldOfTheTargetInstrument() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner, "Cell line");
    Instrument matching = instrumentFromTemplate(owner, template, "Readable scope", "BSL-2");
    Instrument other = instrumentFromTemplate(owner, template, "Other scope", "BSL-1");
    configurationFor(matching.getId(), owner);
    configurationFor(other.getId(), owner);
    ResolvedRuntimeField field = definitionOf(owner, template);

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
    FilterExpression noInstrumentIsReadable =
        new FilterExpression.Comparison("name", Operator.EQUAL, List.of("nothing"), false);
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
    FilterExpression noInstrumentIsReadable =
        new FilterExpression.Comparison("name", Operator.EQUAL, List.of("nothing"), false);

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
