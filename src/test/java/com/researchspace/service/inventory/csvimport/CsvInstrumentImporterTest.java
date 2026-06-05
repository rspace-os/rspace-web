package com.researchspace.service.inventory.csvimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryImportInstrumentImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportInstrumentParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.model.User;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

public class CsvInstrumentImporterTest extends SpringTransactionalTest {

  @Autowired private AutowireCapableBeanFactory beanFactory;

  private CsvInstrumentImporter instrumentCsvImporter;
  @Mock private InventoryIdentifierApiManager mockIdentifierManager;
  @Mock private ApiAvailabilityHandler mockApiHandler;

  private User user;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    user = createAndSaveRandomUser();

    instrumentCsvImporter = beanFactory.createBean(CsvInstrumentImporter.class);
    instrumentCsvImporter.setInventoryIdentifierManager(mockIdentifierManager);
    instrumentCsvImporter.setApiHandler(mockApiHandler);

    when(mockApiHandler.isInventoryAndDataciteEnabled(eq(user))).thenReturn(false);
  }

  @Test
  public void parseInstrumentsCsvFile_suggestsTemplate() throws IOException {
    String csv = "Serial Number,Calibration\nSN-001,0.5\nSN-002,0.7\n";
    InputStream is = IOUtils.toInputStream(csv);

    ApiInventoryImportInstrumentParseResult parseResult =
        instrumentCsvImporter.parseInstrumentsCsvFile("microscopes.csv", is, user);

    assertNotNull(parseResult);
    assertEquals(2, parseResult.getRowsCount());
    assertEquals(2, parseResult.getColumnNames().size());
    assertTrue(parseResult.getColumnNames().contains("Serial Number"));
    assertTrue(parseResult.getColumnNames().contains("Calibration"));

    ApiInstrumentTemplatePost suggestedTemplate = parseResult.getTemplateInfo();
    assertNotNull(suggestedTemplate);
    assertEquals("microscopes", suggestedTemplate.getName());
    assertEquals(2, suggestedTemplate.getFields().size());
  }

  @Test
  public void parseInstrumentsCsvFile_emptyFileThrows() {
    HashMap<String, String> mappings = new HashMap<>();
    mappings.put("Name", "name");
    InputStream empty = IOUtils.toInputStream(" ");

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> instrumentCsvImporter.parseInstrumentsCsvFile("e.csv", empty, user));

    assertTrue(iae.getMessage().contains("CSV file seems to be empty"), iae.getMessage());
  }

  @Test
  public void readCsvIntoImportResult_buildsApiInstrumentsFromRows() throws IOException {
    // template with a single text field 'F1'
    ApiInstrumentTemplate template = new ApiInstrumentTemplate();
    template.setName("Microscope template");
    com.researchspace.api.v1.model.ApiInventoryEntityField field =
        new com.researchspace.api.v1.model.ApiInventoryEntityField();
    field.setName("F1");
    field.setType(com.researchspace.api.v1.model.ApiField.ApiFieldType.TEXT);
    template.getFields().add(field);

    ApiInventoryImportInstrumentImportResult instrumentResult =
        new ApiInventoryImportInstrumentImportResult();
    instrumentResult.addCreatedTemplateResult(template);
    ApiInventoryImportResult importResult = new ApiInventoryImportResult();
    importResult.setInstrumentResult(instrumentResult);

    HashMap<String, String> mappings = new HashMap<>();
    mappings.put("Name", "name");
    InputStream csv = IOUtils.toInputStream("Name,F1\nMicroscope-A,sn-1\nMicroscope-B,sn-2\n");

    instrumentCsvImporter.readCsvIntoImportResult(csv, mappings, importResult, user);

    assertEquals(2, instrumentResult.getResults().size());
    ApiInstrument first = (ApiInstrument) instrumentResult.getResults().get(0).getRecord();
    assertEquals("Microscope-A", first.getName());
    assertEquals(1, first.getFields().size());
    assertEquals("sn-1", first.getFields().get(0).getContent());
    assertEquals(template.getId(), first.getTemplateId());
  }

  @Test
  public void readCsvIntoImportResult_rejectsSampleOnlyDefaultMapping() throws IOException {
    ApiInstrumentTemplate template = new ApiInstrumentTemplate();
    template.setName("t");
    ApiInventoryImportInstrumentImportResult instrumentResult =
        new ApiInventoryImportInstrumentImportResult();
    instrumentResult.addCreatedTemplateResult(template);
    ApiInventoryImportResult importResult = new ApiInventoryImportResult();
    importResult.setInstrumentResult(instrumentResult);

    HashMap<String, String> mappings = new HashMap<>();
    mappings.put("Name", "name");
    mappings.put("Volume", "quantity"); // sample-only mapping
    InputStream csv = IOUtils.toInputStream("Name,Volume\nfoo,5ml\n");

    instrumentCsvImporter.readCsvIntoImportResult(csv, mappings, importResult, user);

    // The row should land as an error, not a successful result
    assertFalse(instrumentResult.getResults().isEmpty());
    assertTrue(
        instrumentResult.getResults().stream().anyMatch(r -> r.getError() != null),
        "expected at least one error result for sample-only mapping");
  }
}
