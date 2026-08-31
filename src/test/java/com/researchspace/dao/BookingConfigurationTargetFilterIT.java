package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  @Autowired private InstrumentDao instrumentDao;
  @Autowired private BookingConfigurationDao bookingConfigurationDao;
  @Autowired private CriteriaBuilderFactory criteriaBuilderFactory;
  @Autowired private InstrumentCustomFieldManager customFields;
  @Autowired private InstrumentEntityApiManager instrumentApiManager;

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
    return new ResourceRegistry(
        List.of(
            ApiV2BookingConfigurationResource.DESCRIPTION,
            ApiV2UserResource.DESCRIPTION,
            ApiV2InstrumentResource.description(readAccess)));
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

  private BookingConfiguration configurationFor(Long instrumentId) {
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
    configurationFor(createBasicInstrumentForUser(owner, "Confocal microscope").getId());
    configurationFor(createBasicInstrumentForUser(owner, "Centrifuge").getId());

    assertEquals(
        1,
        CONFIGURATIONS
            .page(
                criteriaBuilderFactory,
                sessionFactory.getCurrentSession(),
                byTargetName("confocal"),
                null,
                targets(null))
            .resources()
            .size());
    assertEquals(1, count(byTargetName("confocal"), targets(null)));
  }

  @Test
  void appliesTheReadRuleOfTheTargetInsideTheSubquery() {
    User owner = createInitAndLoginAnyUser();
    configurationFor(createBasicInstrumentForUser(owner, "Confocal microscope").getId());
    FilterExpression noInstrumentIsReadable =
        new FilterExpression.Comparison("name", Operator.EQUAL, List.of("nothing"), false);

    assertEquals(1, count(byTargetName("confocal"), targets(null)));
    assertEquals(0, count(byTargetName("confocal"), targets(noInstrumentIsReadable)));
  }

  @Test
  void filtersByACustomFieldOfTheTargetInstrument() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner, "Cell line");
    Instrument matching = instrumentFromTemplate(owner, template, "Readable scope", "BSL-2");
    Instrument other = instrumentFromTemplate(owner, template, "Other scope", "BSL-1");
    configurationFor(matching.getId());
    configurationFor(other.getId());
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
    configurationFor(createBasicInstrumentForUser(owner, "Private confocal").getId());
    ResourceRequest targetFilter = byTargetName("Private confocal");
    ResourceRequest all = ResourceRequest.unpaged(null);
    RelationshipReadAccess readable = targets(null);
    RelationshipReadAccess unreadable =
        targets(new FilterExpression.Comparison("name", Operator.EQUAL, List.of("nothing"), false));

    assertEquals(1, bookingConfigurationDao.countResources(targetFilter, readable));
    assertEquals(0, bookingConfigurationDao.countResources(targetFilter, unreadable));
    assertEquals(0, bookingConfigurationDao.getResources(targetFilter, unreadable).total());
    assertEquals(1, bookingConfigurationDao.countResources(all, unreadable));
    assertEquals(1, bookingConfigurationDao.getResources(all, unreadable).total());
  }

  @Test
  void narrowingByTargetIdsPrunesWithoutLosingTheTargetReadRule() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument confocal = createBasicInstrumentForUser(owner, "Confocal microscope");
    ApiInstrument centrifuge = createBasicInstrumentForUser(owner, "Centrifuge");
    configurationFor(confocal.getId());
    configurationFor(centrifuge.getId());
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
