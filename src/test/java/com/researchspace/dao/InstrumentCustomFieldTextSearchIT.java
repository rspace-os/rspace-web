package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryEntityField.ApiInventoryFieldDef;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.service.inventory.ExtraFieldRuntimeManager;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import com.researchspace.service.inventory.impl.ExtraFieldRuntimeManagerImpl;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InstrumentCustomFieldTextSearchIT extends RealTransactionSpringTestBase {

  @Autowired private InstrumentDao instrumentDao;
  @Autowired private InstrumentReadAccess instrumentReadAccess;
  @Autowired private InstrumentCustomFieldManager customFields;
  @Autowired private ExtraFieldDao extraFieldDao;

  private User owner;
  private ResolvedRuntimeField hazardClass;
  private ResolvedRuntimeField notes;

  @BeforeEach
  public void setUpTextSearch() throws Exception {
    owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner, "Cell line");
    doInTransaction(
        () -> {
          InstrumentCustomFieldTestUtils.instrumentFromTemplate(
              instrumentDao, instrumentApiMgr, owner, template, "Exact", "BSL-2");
          InstrumentCustomFieldTestUtils.instrumentFromTemplate(
              instrumentDao, instrumentApiMgr, owner, template, "Prefixed", "XBSL-2");
          InstrumentCustomFieldTestUtils.instrumentFromTemplate(
              instrumentDao, instrumentApiMgr, owner, template, "Phrase", "Level BSL-2 lab");
          InstrumentCustomFieldTestUtils.instrumentFromTemplate(
              instrumentDao, instrumentApiMgr, owner, template, "Other level", "Level BSL-3 lab");
        });
    doInTransaction(() -> instrumentWithExtraField("Ad hoc", "Notes", "BSL-2 cabinet"));
    hazardClass =
        doInTransaction(
            () -> InstrumentCustomFieldTestUtils.definitionOf(customFields, owner, template));
    notes = doInTransaction(this::extraFieldDefinition);
  }

  @Test
  public void matchesLikeBySubstringForEachWord() throws Exception {
    assertEquals(List.of("Exact", "Other level", "Phrase", "Prefixed"), like("BSL"));
    assertEquals(List.of("Exact", "Phrase", "Prefixed"), like("BSL-2"));
  }

  @Test
  public void matchesEveryWordOfALikeInAnyOrder() throws Exception {
    assertEquals(List.of("Phrase"), like("BSL-2 lab"));
    assertEquals(List.of("Phrase"), like("lab BSL-2"));
    assertEquals(List.of(), like("BSL-2 freezer"));
  }

  @Test
  public void matchesPartialWordsAsSubstrings() throws Exception {
    assertEquals(List.of("Exact", "Phrase", "Prefixed"), like("SL-2"));
  }

  @Test
  public void keepsContainsAnExactSubstringMatch() throws Exception {
    assertEquals(List.of("Exact", "Other level", "Phrase", "Prefixed"), contains("BSL"));
    assertEquals(List.of("Exact", "Phrase", "Prefixed"), contains("SL-2"));
    assertEquals(List.of("Phrase"), contains("BSL-2 lab"));
    assertEquals(List.of(), contains("lab BSL-2"));
  }

  @Test
  public void matchesChoiceValuesAsExactJsonMembers() throws Exception {
    List<String> options =
        List.of("BSL-2", "bsl-2", "prefix\"BSL-2", "prefix\\\"BSL-2", "prefix\\BSL-2");
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("Choice JSON membership");
    ApiInventoryEntityField choiceDefinition =
        createBasicApiSampleOptionsField("choice", ApiFieldType.CHOICE, List.of());
    choiceDefinition.setDefinition(new ApiInventoryFieldDef(options, true));
    templatePost.getFields().add(choiceDefinition);
    ApiInstrumentTemplate template = instrumentApiMgr.createInstrumentTemplate(templatePost, owner);

    doInTransaction(
        () -> {
          instrumentWithChoiceSelection(template, "Choice exact", List.of("BSL-2"));
          instrumentWithChoiceSelection(template, "Choice lowercase", List.of("bsl-2"));
          instrumentWithChoiceSelection(template, "Choice quoted", List.of("prefix\"BSL-2"));
          instrumentWithChoiceSelection(
              template, "Choice escaped quote", List.of("prefix\\\"BSL-2"));
          instrumentWithChoiceSelection(template, "Choice backslash", List.of("prefix\\BSL-2"));
          instrumentWithChoiceSelection(template, "Choice empty", List.of());
          instrumentWithChoiceSelection(template, "Choice malformed", List.of())
              .setData("[\"unfinished");
          instrumentWithChoiceSelection(template, "Choice blank", List.of()).setData("");
        });
    ResolvedRuntimeField choice =
        doInTransaction(
            () -> InstrumentCustomFieldTestUtils.definitionOf(customFields, owner, template));

    assertEquals(List.of("Choice exact"), matches(choice, Operator.CONTAINS, "BSL-2"));
    assertEquals(List.of("Choice exact"), matches(choice, Operator.IN, "BSL-2"));
    assertEquals(List.of("Choice lowercase"), matches(choice, Operator.CONTAINS, "bsl-2"));
    assertEquals(List.of("Choice lowercase"), matches(choice, Operator.IN, "bsl-2"));
    assertEquals(List.of("Choice quoted"), matches(choice, Operator.CONTAINS, "prefix\"BSL-2"));
    assertEquals(List.of("Choice escaped quote"), matches(choice, Operator.IN, "prefix\\\"BSL-2"));
    assertEquals(List.of("Choice backslash"), matches(choice, Operator.CONTAINS, "prefix\\BSL-2"));
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

  private InventoryChoiceField instrumentWithChoiceSelection(
      ApiInstrumentTemplate template, String name, List<String> selectedOptions) {
    ApiInstrument request = new ApiInstrument();
    request.setName(name);
    request.setTemplateId(template.getId());
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, owner);
    Instrument instrument = instrumentDao.get(created.getId());
    InventoryChoiceField choiceField =
        instrument.getActiveFields().stream()
            .filter(InventoryChoiceField.class::isInstance)
            .map(InventoryChoiceField.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No choice field in template"));
    choiceField.setSelectedOptions(selectedOptions);
    instrumentDao.save(instrument);
    return choiceField;
  }

  private ResolvedRuntimeField extraFieldDefinition() {
    ExtraFieldRuntimeManager<Instrument> extraFields =
        new ExtraFieldRuntimeManagerImpl<>(
            extraFieldDao,
            Instrument.class,
            ApiV2InstrumentResource.DESCRIPTION,
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

  private List<String> like(String term) throws Exception {
    return matches(hazardClass, Operator.LIKE, term);
  }

  private List<String> contains(String term) throws Exception {
    return matches(hazardClass, Operator.CONTAINS, term);
  }

  private List<String> matches(ResolvedRuntimeField field, Operator operator, String term)
      throws Exception {
    ResourceRequest request = InstrumentCustomFieldTestUtils.filter(field, operator, term);
    return doInTransaction(
        () ->
            InstrumentCustomFieldTestUtils.matches(
                instrumentDao, instrumentReadAccess, request, owner));
  }

  private Instrument instrumentWithExtraField(String name, String fieldName, String value) {
    Instrument instrument = instrumentDao.get(createBasicInstrumentForUser(owner, name).getId());
    ExtraField field = recordFactory.createExtraField(fieldName, FieldType.TEXT, owner, instrument);
    field.setData(value);
    instrument.addExtraField(field);
    instrumentDao.save(instrument);
    return instrument;
  }
}
