package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.model.User;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InstrumentCustomFieldValuesIT extends RealTransactionSpringTestBase {

  @Autowired private InstrumentDao instrumentDao;
  @Autowired private InstrumentCustomFieldManager customFields;

  private User owner;
  private RuntimeFieldDefinition hazardClass;

  private final Set<Object> fixtureIds = new LinkedHashSet<>();

  @Before
  public void setUpValues() throws Exception {
    owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = stringFieldTemplate("Cell line");
    doInTransaction(
        () -> {
          instrumentFromTemplate(template, "First", "BSL-2");
        });
    hazardClass = doInTransaction(() -> definitionOf(template));
  }

  @Test
  public void projectsValuesOnlyForInstrumentsTheActorMayRead() throws Exception {
    Set<String> requested = Set.of(hazardClass.id());

    Map<Object, Map<String, Object>> mine =
        doInTransaction(() -> customFields.valuesForIds(fixtureIds, requested, owner));
    assertTrue(mine.values().stream().anyMatch(values -> values.containsValue("BSL-2")));

    User stranger = createInitAndLoginAnyUser();
    Map<Object, Map<String, Object>> theirs =
        doInTransaction(() -> customFields.valuesForIds(fixtureIds, requested, stranger));
    assertEquals(Map.of(), theirs);
  }

  @Test
  public void publishesThatItsFieldsCanBeProjectedThroughARelationship() {
    assertTrue(customFields.projectsThroughRelationship());
  }

  private ApiInstrumentTemplate stringFieldTemplate(String name) {
    ApiInstrumentTemplatePost post = new ApiInstrumentTemplatePost();
    post.setName(name);
    post.getFields().add(createBasicApiSampleField("hazard class", ApiFieldType.STRING, ""));
    return instrumentApiMgr.createInstrumentTemplate(post, owner);
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
    fixtureIds.add(instrument.getId());
  }

  private RuntimeFieldDefinition definitionOf(ApiInstrumentTemplate template) {
    String templateId = String.valueOf(template.getId());
    return customFields
        .discover(owner, new RuntimeFieldCatalogQuery(null, Set.of(), 1, 50))
        .fields()
        .stream()
        .filter(field -> field.sourceId().endsWith(templateId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No readable definition for " + templateId));
  }
}
