package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.InventoryImportApiController.ApiInventoryImportSettingsPost;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.field.LocalizedIllegalStateException;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InventoryImportManager;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class InventoryImportApiControllerTest {

  @Mock private InventoryImportManager importManager;
  @Mock private IControllerInputValidator inputValidator;
  @Mock private InventoryImportPostFullValidator importPostFullValidator;
  @InjectMocks private InventoryImportApiController controller;

  @Test
  void databaseRefusalIsReportedWithoutSqlText() throws Exception {
    DataIntegrityViolationException refusal =
        new DataIntegrityViolationException(
            "could not execute statement [Data truncation: Data too long for column"
                + " 'description' at row 1] [insert into InstrumentEntity (description) values"
                + " (?)]");
    when(importManager.importInventoryCsvFiles(any(), any())).thenThrow(refusal);

    LocalizedIllegalStateException thrown =
        assertThrows(
            LocalizedIllegalStateException.class,
            () ->
                controller.importFromCsv(
                    null,
                    null,
                    null,
                    null,
                    new ApiInventoryImportSettingsPost(),
                    TestFactory.createAnyUser("importer")));

    assertSame(refusal, thrown.getCause());
    assertEquals(
        "The database refused the import and nothing was imported. Check the rows for values"
            + " longer than their fields allow, then try again.",
        new MessageSourceUtils(new JsonMessageSource()).getExceptionMessage(thrown));
  }
}
