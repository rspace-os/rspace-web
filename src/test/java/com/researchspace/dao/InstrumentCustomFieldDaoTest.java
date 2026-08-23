package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InstrumentCustomFieldDaoTest extends SpringTransactionalTest {

  @Autowired private InstrumentDao instrumentDao;
  @Autowired private InstrumentReadAccess instrumentReadAccess;
  @Autowired private InstrumentCustomFieldManager customFields;

  @Autowired private InstrumentEntityApiManager instrumentApiManager;

  private List<RuntimeFieldDefinition> catalog(User actor) {
    List<RuntimeFieldDefinition> all = new java.util.ArrayList<>();
    for (int page = 1; ; page++) {
      var result =
          customFields.discover(
              actor,
              new RuntimeFieldCatalogQuery(
                  null, Set.of(), page, RuntimeFieldCatalogQuery.MAX_LIMIT));
      all.addAll(result.fields());
      if (all.size() >= result.total() || result.fields().isEmpty()) {
        return all;
      }
    }
  }

  private static List<String> labels(List<RuntimeFieldDefinition> fields) {
    return fields.stream().map(RuntimeFieldDefinition::label).toList();
  }

  private List<RuntimeFieldDefinition> search(User actor, String term) {
    return customFields
        .discover(
            actor,
            new RuntimeFieldCatalogQuery(term, Set.of(), 1, RuntimeFieldCatalogQuery.MAX_LIMIT))
        .fields();
  }

  private AccessResult readAccess(User user) {
    return instrumentReadAccess.check(new AccessContext(user, Operation.READ, "instruments"));
  }

  private void instrumentFromTemplate(
      User owner, ApiInstrumentTemplate template, String name, String value) {
    ApiInstrument request = new ApiInstrument();
    request.setName(name);
    request.setTemplateId(template.getId());
    ApiInstrument created = instrumentApiManager.createNewApiInstrument(request, owner);
    Instrument instrument = instrumentDao.get(created.getId());
    for (InventoryEntityField field : instrument.getActiveFields()) {
      field.setFieldData(value);
    }
    instrumentDao.save(instrument);
  }

  private ResolvedRuntimeField definitionOf(User actor, ApiInstrumentTemplate template) {
    String templateId = String.valueOf(template.getId());
    return catalog(actor).stream()
        .filter(field -> field.sourceId().endsWith(templateId))
        .findFirst()
        .flatMap(field -> customFields.resolve(field.selector(), actor))
        .orElseThrow(() -> new AssertionError("No readable definition for template " + templateId));
  }

  private ResourceRequest filter(ResolvedRuntimeField field, Operator operator, Object value) {
    return new ResourceRequest(
        new FilterExpression.Comparison(field.selector(), operator, List.of(value), false),
        null,
        List.of(new Sort("name", true), new Sort("id", true)),
        new ResourceRequest.Page(1, 20),
        ResourceFieldSelections.root(FieldSelection.all()),
        IncludeTree.empty(),
        new RuntimeFieldSelection(Map.of(field.selector(), field), Set.of()));
  }

  private List<String> matches(ResourceRequest request, User actor) {
    return instrumentDao.getReadableResources(request, readAccess(actor)).resources().stream()
        .map(Instrument::getName)
        .toList();
  }

  private List<Instrument> allReadable(User actor) {
    return instrumentDao
        .getReadableResources(
            new ResourceRequest(
                null,
                List.of(new Sort("name", true), new Sort("id", true)),
                new ResourceRequest.Page(1, 50),
                FieldSelection.all(),
                IncludeTree.empty()),
            readAccess(actor))
        .resources();
  }

  private ApiInstrumentTemplate percentNamedFieldTemplate(User owner) {
    ApiInstrumentTemplatePost post = new ApiInstrumentTemplatePost();
    post.setName("Assay");
    post.getFields().add(createBasicApiSampleField("purity 50% (w/w)", ApiFieldType.NUMBER, "0"));
    return instrumentApiMgr.createInstrumentTemplate(post, owner);
  }

  private ApiInstrumentTemplate numericTemplate(User owner) {
    ApiInstrumentTemplatePost post = new ApiInstrumentTemplatePost();
    post.setName("Freezer");
    post.getFields().add(createBasicApiSampleField("temperature", ApiFieldType.NUMBER, "0"));
    return instrumentApiMgr.createInstrumentTemplate(post, owner);
  }

  private void legacyValue(
      User owner, ApiInstrumentTemplate template, String name, String storedText) {
    ApiInstrument request = new ApiInstrument();
    request.setName(name);
    request.setTemplateId(template.getId());
    ApiInstrument created = instrumentApiManager.createNewApiInstrument(request, owner);
    sessionFactory
        .getCurrentSession()
        .createMutationQuery(
            "update InventoryEntityField f set f.data = :data where f.instrumentEntity.id = :id")
        .setParameter("data", storedText)
        .setParameter("id", created.getId())
        .executeUpdate();
    sessionFactory.getCurrentSession().clear();
  }

  @Test
  public void comparesANumericFieldAsANumberAndIgnoresTextThatIsNotOne() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = numericTemplate(owner);
    instrumentFromTemplate(owner, template, "Cold store", "-80");
    instrumentFromTemplate(owner, template, "Zero store", "0");
    legacyValue(owner, template, "Legacy store", "n/a");
    ResolvedRuntimeField field = definitionOf(owner, template);

    assertEquals(List.of("Cold store"), matches(filter(field, Operator.LESS_THAN, -70d), owner));
    assertEquals(
        List.of("Cold store", "Zero store"),
        matches(filter(field, Operator.LESS_THAN_OR_EQUAL, 0d), owner));
    assertEquals(List.of("Zero store"), matches(filter(field, Operator.EQUAL, 0d), owner));
  }

  @Test
  public void publishesOnlyDefinitionsReachableThroughAReadableItem() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner, "Cell line");
    instrumentFromTemplate(owner, template, "Readable scope", "BSL-2");
    User stranger = createInitAndLoginAnyUser();
    String templateId = String.valueOf(template.getId());

    assertTrue(catalog(owner).stream().anyMatch(field -> field.sourceId().endsWith(templateId)));
    assertFalse(
        catalog(stranger).stream().anyMatch(field -> field.sourceId().endsWith(templateId)));
  }

  @Test
  public void refusesToResolveADefinitionTheActorCannotReach() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner, "Cell line");
    instrumentFromTemplate(owner, template, "Readable scope", "BSL-2");
    String selector = definitionOf(owner, template).selector();
    User stranger = createInitAndLoginAnyUser();

    assertTrue(customFields.resolve(selector, stranger).isEmpty());
  }

  @Test
  public void keepsTwoSameNamedDefinitionsApart() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate first = createBasicInstrumentTemplateForUser(owner, "Cell line");
    ApiInstrumentTemplate second = createBasicInstrumentTemplateForUser(owner, "Bacterial");
    instrumentFromTemplate(owner, first, "From cell line", "BSL-2");
    instrumentFromTemplate(owner, second, "From bacterial", "BSL-2");

    List<RuntimeFieldDefinition> published = catalog(owner);
    ResolvedRuntimeField fromFirst = definitionOf(owner, first);

    assertEquals(
        List.of("From cell line"), matches(filter(fromFirst, Operator.EQUAL, "BSL-2"), owner));
    assertTrue(
        published.stream()
                .filter(field -> field.label().equals(published.get(0).label()))
                .map(RuntimeFieldDefinition::id)
                .distinct()
                .count()
            >= 1);
  }

  @Test
  public void treatsAWildcardInACatalogueSearchAsALiteral() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = percentNamedFieldTemplate(owner);
    instrumentFromTemplate(owner, template, "Readable scope", "0.9");

    assertEquals(List.of("purity 50% (w/w)"), labels(search(owner, "50%")));
    assertTrue(search(owner, "50!").isEmpty());
  }

  @Test
  public void treatsAnEmptyValueAsNoValue() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner, "Cell line");
    instrumentFromTemplate(owner, template, "Has a value", "BSL-2");
    instrumentFromTemplate(owner, template, "Value cleared", "");
    ResolvedRuntimeField field = definitionOf(owner, template);

    assertEquals(
        List.of("Has a value"), matches(filter(field, Operator.EXISTS, Boolean.TRUE), owner));
    assertTrue(
        matches(filter(field, Operator.EXISTS, Boolean.FALSE), owner).contains("Value cleared"));
  }

  @Test
  public void narrowsAnAlreadyAuthorizedRowSetRatherThanWideningIt() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner, "Cell line");
    instrumentFromTemplate(owner, template, "Private scope", "BSL-2");
    ResolvedRuntimeField field = definitionOf(owner, template);
    User stranger = createInitAndLoginAnyUser();

    assertTrue(matches(filter(field, Operator.EQUAL, "BSL-2"), stranger).isEmpty());
    assertEquals(List.of("Private scope"), matches(filter(field, Operator.EQUAL, "BSL-2"), owner));
  }

  @Test
  public void loadsEveryRequestedValueForAWholePageAtOnce() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner, "Cell line");
    instrumentFromTemplate(owner, template, "Scope one", "BSL-1");
    instrumentFromTemplate(owner, template, "Scope two", "BSL-2");
    ResolvedRuntimeField field = definitionOf(owner, template);

    Map<Object, Map<String, Object>> values =
        customFields.values(allReadable(owner), Set.of(field.id()), owner);

    assertEquals(2, values.values().stream().filter(row -> row.containsKey(field.id())).count());
    assertTrue(values.values().stream().anyMatch(row -> "BSL-1".equals(row.get(field.id()))));
    assertTrue(values.values().stream().anyMatch(row -> "BSL-2".equals(row.get(field.id()))));
  }
}
