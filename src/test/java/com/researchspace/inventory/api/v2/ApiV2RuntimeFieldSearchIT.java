package com.researchspace.inventory.api.v2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiV2RuntimeFieldSearchIT extends SpringTransactionalTest {

  private final ApiV2ResourceCatalog catalog;
  private final ApiV2ControllerAdvice advice;
  private final InstrumentDao instrumentDao;
  private MockMvc mvc;

  @Autowired
  ApiV2RuntimeFieldSearchIT(
      ApiV2ResourceCatalog catalog, ApiV2ControllerAdvice advice, InstrumentDao instrumentDao) {
    this.catalog = catalog;
    this.advice = advice;
    this.instrumentDao = instrumentDao;
  }

  @BeforeEach
  void buildMvc() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ApiV2CrudController(catalog))
            .setControllerAdvice(advice)
            .build();
  }

  @Test
  void searchesBothInstrumentNamespacesInOneRequest() throws Exception {
    var owner = createInitAndLoginAnyUser();
    Instrument instrument =
        instrumentDao.get(createBasicInstrumentForUser(owner, "Searched instrument").getId());
    ExtraField field =
        recordFactory.createExtraField("Search room", FieldType.TEXT, owner, instrument);
    field.setData("B12");
    instrument.addExtraField(field);
    instrumentDao.save(instrument);
    sessionFactory.getCurrentSession().flush();

    mvc.perform(
            get("/api/v2/instruments/fields")
                .param("namespace", "customFields", "extraFields")
                .param("q", "search room")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.catalogs.customFields.fields.length()").value(0))
        .andExpect(jsonPath("$.catalogs.customFields.hasMore").value(false))
        .andExpect(jsonPath("$.catalogs.extraFields.fields[0].label").value("Search room"))
        .andExpect(jsonPath("$.catalogs.extraFields.page").value(1));
  }

  @Test
  void refusesACallerTheCollectionReadPolicyRefuses() throws Exception {
    mvc.perform(
            get("/api/v2/instruments/fields")
                .param("namespace", "customFields", "extraFields")
                .param("q", "room"))
        .andExpect(status().isUnauthorized());
  }
}
