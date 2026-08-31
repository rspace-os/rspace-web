package com.researchspace.dao;

import static org.junit.Assert.assertEquals;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
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
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.search.customfield.RuntimeFieldTextSearch;
import com.researchspace.service.inventory.ExtraFieldRuntimeManager;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import com.researchspace.service.inventory.impl.ExtraFieldRuntimeManagerImpl;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"collections.textSearch.enabled=true"})
public class InstrumentCustomFieldTextSearchIT extends RealTransactionSpringTestBase {

  @Autowired private InstrumentDao instrumentDao;
  @Autowired private InstrumentReadAccess instrumentReadAccess;
  @Autowired private InstrumentCustomFieldManager customFields;
  @Autowired private RuntimeFieldTextSearch textSearch;
  @Autowired private ExtraFieldDao extraFieldDao;

  private User owner;
  private ResolvedRuntimeField hazardClass;
  private ResolvedRuntimeField notes;

  @Before
  public void setUpTextSearch() throws Exception {
    owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner, "Cell line");
    doInTransaction(
        () -> {
          instrumentFromTemplate(template, "Exact", "BSL-2");
          instrumentFromTemplate(template, "Prefixed", "XBSL-2");
          instrumentFromTemplate(template, "Phrase", "Level BSL-2 lab");
        });
    doInTransaction(() -> instrumentWithExtraField("Ad hoc", "Notes", "BSL-2 cabinet"));
    hazardClass = doInTransaction(() -> definitionOf(template));
    notes = doInTransaction(this::extraFieldDefinition);
    doInTransaction(() -> textSearch.reindexAll());
  }

  @Test
  public void matchesLikeByWholeWordWhenTheIndexCanAnswer() throws Exception {
    assertEquals(List.of("Exact", "Phrase"), like("BSL"));
  }

  @Test
  public void matchesEveryWordOfALikeInAnyOrder() throws Exception {
    assertEquals(List.of("Phrase"), like("BSL-2 lab"));
    assertEquals(List.of("Phrase"), like("lab BSL-2"));
    assertEquals(List.of(), like("BSL-2 freezer"));
  }

  @Test
  public void fallsBackToASubstringMatchForAPartialWord() throws Exception {
    assertEquals(List.of("Exact", "Phrase", "Prefixed"), like("SL-2"));
  }

  @Test
  public void keepsContainsAnExactSubstringMatch() throws Exception {
    assertEquals(List.of("Exact", "Phrase", "Prefixed"), contains("BSL"));
    assertEquals(List.of("Exact", "Phrase", "Prefixed"), contains("SL-2"));
    assertEquals(List.of("Phrase"), contains("BSL-2 lab"));
    assertEquals(List.of(), contains("lab BSL-2"));
  }

  private List<String> like(String term) throws Exception {
    return matches(hazardClass, Operator.LIKE, term);
  }

  private List<String> contains(String term) throws Exception {
    return matches(hazardClass, Operator.CONTAINS, term);
  }

  private List<String> matches(ResolvedRuntimeField field, Operator operator, String term)
      throws Exception {
    ResourceRequest request =
        new ResourceRequest(
            new FilterExpression.Comparison(field.selector(), operator, List.of(term), false),
            null,
            List.of(new Sort("name", true), new Sort("id", true)),
            new ResourceRequest.Page(1, 20),
            ResourceFieldSelections.root(FieldSelection.all()),
            IncludeTree.empty(),
            new RuntimeFieldSelection(Map.of(field.selector(), field), Set.of()));
    return doInTransaction(
        () -> {
          AccessResult access =
              instrumentReadAccess.check(new AccessContext(owner, Operation.READ, "instruments"));
          return instrumentDao.getReadableResources(request, access).resources().stream()
              .map(Instrument::getName)
              .toList();
        });
  }

  private void instrumentFromTemplate(ApiInstrumentTemplate template, String name, String value) {
    ApiInstrument request = new ApiInstrument();
    request.setName(name);
    request.setTemplateId(template.getId());
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, owner);
    Instrument instrument = instrumentDao.get(created.getId());
    for (InventoryEntityField field : instrument.getActiveFields()) {
      field.setFieldData(value);
    }
    instrumentDao.save(instrument);
  }

  private ResolvedRuntimeField definitionOf(ApiInstrumentTemplate template) {
    String templateId = String.valueOf(template.getId());
    return customFields
        .discover(owner, new RuntimeFieldCatalogQuery(null, Set.of(), 1, 50))
        .fields()
        .stream()
        .filter(field -> field.sourceId().endsWith(templateId))
        .findFirst()
        .flatMap(field -> customFields.resolve(field.selector(), owner))
        .orElseThrow(() -> new AssertionError("No readable definition for " + templateId));
  }

  @Test
  public void matchesAnAdHocExtraFieldByWholeWordThroughTheIndex() throws Exception {
    assertEquals(List.of("Ad hoc"), matches(notes, Operator.LIKE, "cabinet"));
    assertEquals(List.of("Ad hoc"), matches(notes, Operator.LIKE, "cabinet BSL-2"));
    assertEquals(List.of(), matches(notes, Operator.LIKE, "cabinet freezer"));
  }

  @Test
  public void fallsBackToTheDatabaseForAPartialWordOfAnExtraField() throws Exception {
    assertEquals(List.of("Ad hoc"), matches(notes, Operator.LIKE, "cabin"));
    assertEquals(List.of("Ad hoc"), matches(notes, Operator.CONTAINS, "abinet"));
  }

  private Instrument instrumentWithExtraField(String name, String fieldName, String value) {
    Instrument instrument = instrumentDao.get(createBasicInstrumentForUser(owner, name).getId());
    ExtraField field = recordFactory.createExtraField(fieldName, FieldType.TEXT, owner, instrument);
    field.setData(value);
    instrument.addExtraField(field);
    instrumentDao.save(instrument);
    return instrument;
  }

  private ResolvedRuntimeField extraFieldDefinition() {
    ExtraFieldRuntimeManager<Instrument> extraFields =
        new ExtraFieldRuntimeManagerImpl<>(
            extraFieldDao,
            Instrument.class,
            com.researchspace.inventory.model.ApiV2InstrumentResource.DESCRIPTION,
            "instrumentEntity",
            instrumentReadAccess::check);
    return extraFields
        .discover(owner, new RuntimeFieldCatalogQuery(null, Set.of(), 1, 50))
        .fields()
        .stream()
        .filter(
            field -> "Notes".equals(field.label()) && field.type() == RuntimeFieldValueType.TEXT)
        .findFirst()
        .flatMap(field -> extraFields.resolve(field.selector(), owner))
        .orElseThrow(() -> new AssertionError("No readable ad-hoc definition"));
  }
}
