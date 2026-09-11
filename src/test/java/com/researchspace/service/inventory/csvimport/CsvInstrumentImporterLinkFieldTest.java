package com.researchspace.service.inventory.csvimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryImportInstrumentImportResult;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Instrument import applies the same link-cell handling as sample import. */
public class CsvInstrumentImporterLinkFieldTest {

  private final CsvInstrumentImporter importer = new CsvInstrumentImporter();
  private final ApiInventoryImportInstrumentImportResult result =
      new ApiInventoryImportInstrumentImportResult();

  @BeforeEach
  void setUp() {
    CsvLinkValueParser parser = new CsvLinkValueParser();
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example.com");
    parser.properties = properties;
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    when(messages.getMessage(eq("errors.inventory.import.linkValueInvalid"), any()))
        .thenReturn("bad link cell");
    parser.messages = messages;
    importer.linkParser = parser;
    importer.messages = messages;

    ApiInstrumentTemplate template = new ApiInstrumentTemplate();
    ApiInventoryEntityField linkField = new ApiInventoryEntityField();
    linkField.setName("Calibrated by");
    linkField.setType(ApiFieldType.LINK);
    template.getFields().add(linkField);
    result.addCreatedTemplateResult(template);
  }

  @Test
  void linkCellBecomesInstrumentFieldLink() {
    List<String[]> lines =
        List.<String[]>of(
            new String[] {"i1", "IsCalibratedBy https://rspace.example.com/globalId/SD7"});

    importer.convertLinesToInstruments(result, lines, Map.of(0, "name"), 2, new User("u"));

    assertEquals(1, result.getSuccessCount());
    ApiInventoryEntityField field =
        ((ApiInstrument) result.getResults().get(0).getRecord()).getFields().get(0);
    ApiInventoryLink link = field.getLink();
    assertEquals("IsCalibratedBy", link.getRelationType());
    assertEquals("SD7", link.getTargetGlobalId());
    assertNull(link.getVersionPin());
    assertNull(field.getContent());
  }

  @Test
  void malformedLinkCellFailsTheRow() {
    List<String[]> lines = List.<String[]>of(new String[] {"i1", "nonsense"});

    importer.convertLinesToInstruments(result, lines, Map.of(0, "name"), 2, new User("u"));

    assertEquals(1, result.getErrorCount());
    assertEquals("bad link cell", result.getResults().get(0).getError().getErrors().get(0));
  }
}
