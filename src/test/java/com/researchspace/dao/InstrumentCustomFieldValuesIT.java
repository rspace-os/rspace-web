package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.model.User;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InstrumentCustomFieldValuesIT extends RealTransactionSpringTestBase {

  private final InstrumentDao instrumentDao;
  private final InstrumentCustomFieldManager customFields;

  @Autowired
  InstrumentCustomFieldValuesIT(
      InstrumentDao instrumentDao, InstrumentCustomFieldManager customFields) {
    this.instrumentDao = instrumentDao;
    this.customFields = customFields;
  }

  private User owner;
  private ResolvedRuntimeField hazardClass;
  private Object fixtureId;

  @BeforeEach
  public void setUpValues() throws Exception {
    owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = stringFieldTemplate("Cell line");
    fixtureId =
        doInTransaction(
                () ->
                    InstrumentCustomFieldTestUtils.instrumentFromTemplate(
                        instrumentDao, instrumentApiMgr, owner, template, "First", "BSL-2"))
            .getId();
    hazardClass =
        doInTransaction(
            () -> InstrumentCustomFieldTestUtils.definitionOf(customFields, owner, template));
  }

  @Test
  public void projectsValuesOnlyForInstrumentsTheActorMayRead() throws Exception {
    Set<String> requested = Set.of(hazardClass.id());

    Map<Object, Map<String, Object>> mine =
        doInTransaction(() -> customFields.valuesForIds(Set.of(fixtureId), requested, owner));
    assertTrue(mine.values().stream().anyMatch(values -> values.containsValue("BSL-2")));

    User stranger = createInitAndLoginAnyUser();
    Map<Object, Map<String, Object>> theirs =
        doInTransaction(() -> customFields.valuesForIds(Set.of(fixtureId), requested, stranger));
    assertEquals(Map.of(), theirs);
  }

  private ApiInstrumentTemplate stringFieldTemplate(String name) {
    ApiInstrumentTemplatePost post = new ApiInstrumentTemplatePost();
    post.setName(name);
    post.getFields().add(createBasicApiSampleField("hazard class", ApiFieldType.STRING, ""));
    return instrumentApiMgr.createInstrumentTemplate(post, owner);
  }
}
