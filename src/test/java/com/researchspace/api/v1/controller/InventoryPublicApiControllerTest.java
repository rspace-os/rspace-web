package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import org.junit.jupiter.api.Test;

class InventoryPublicApiControllerTest {

  private final InventoryPublicApiController controller = new InventoryPublicApiController();

  private ApiInstrument instrumentWithOneField(boolean customFieldsOnPublicPage) {
    ApiInstrument instrument = new ApiInstrument();
    ApiInventoryEntityField field = new ApiInventoryEntityField();
    field.setName("Manufacturer");
    instrument.getFields().add(field);
    ApiInventoryDOI doi = new ApiInventoryDOI();
    doi.setCustomFieldsOnPublicPage(customFieldsOnPublicPage);
    instrument.getIdentifiers().add(doi);
    return instrument;
  }

  /**
   * The public landing page of an accepted instrument PID must carry the instrument's fields when
   * the identifier opts in, just as sample pages carry sample fields (RSDEV-1260). Before the
   * instrument arm existed, the copy silently dropped them.
   */
  @Test
  void publicViewOfInstrumentCarriesFieldsWhenCustomFieldsArePublic() {
    ApiInventoryRecordInfo copy =
        controller.getRecordCopyLimitedToPublishedDetails(instrumentWithOneField(true));

    assertEquals(1, ((ApiInstrument) copy).getFields().size());
    assertEquals("Manufacturer", ((ApiInstrument) copy).getFields().get(0).getName());
  }

  @Test
  void publicViewOfInstrumentDropsFieldsWhenCustomFieldsAreNotPublic() {
    ApiInventoryRecordInfo copy =
        controller.getRecordCopyLimitedToPublishedDetails(instrumentWithOneField(false));

    assertTrue(((ApiInstrument) copy).getFields().isEmpty());
  }
}
