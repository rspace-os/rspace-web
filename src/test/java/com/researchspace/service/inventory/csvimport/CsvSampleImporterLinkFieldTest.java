package com.researchspace.service.inventory.csvimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryImportSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A CSV column mapped to a Link template field becomes the sample field's link, not its content.
 */
public class CsvSampleImporterLinkFieldTest {

  private final CsvSampleImporter importer = new CsvSampleImporter();
  private final ApiInventoryImportSampleImportResult result =
      new ApiInventoryImportSampleImportResult();

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

    ApiSampleTemplate template = new ApiSampleTemplate();
    ApiInventoryEntityField linkField = new ApiInventoryEntityField();
    linkField.setName("Derived from");
    linkField.setType(ApiFieldType.LINK);
    template.getFields().add(linkField);
    result.addCreatedTemplateResult(template);
  }

  @Test
  void linkCellBecomesFieldLinkAndBlankCellLeavesNoLink() {
    List<String[]> lines =
        List.of(
            new String[] {"s1", "IsDerivedFrom https://rspace.example.com/globalId/SA1v3"},
            new String[] {"s2", ""});

    importer.convertLinesToSamples(result, lines, Map.of(0, "name"), 2, new User("u"));

    assertEquals(2, result.getSuccessCount());
    ApiInventoryLink link = field(0).getLink();
    assertEquals("IsDerivedFrom", link.getRelationType());
    assertEquals("SA1", link.getTargetGlobalId());
    assertEquals(3L, link.getVersionPin());
    assertTrue(link.isSkipTargetCheck());
    assertNull(field(0).getContent());
    assertNull(field(1).getLink());
  }

  @Test
  void malformedLinkCellFailsTheRowOnly() {
    List<String[]> lines =
        List.of(
            new String[] {"s1", "not a link"},
            new String[] {"s2", "Cites https://rspace.example.com/globalId/SD9"});

    importer.convertLinesToSamples(result, lines, Map.of(0, "name"), 2, new User("u"));

    assertEquals(1, result.getErrorCount());
    assertEquals(1, result.getSuccessCount());
    assertEquals("bad link cell", result.getResults().get(0).getError().getErrors().get(0));
  }

  private ApiInventoryEntityField field(int row) {
    ApiSampleWithFullSubSamples sample =
        (ApiSampleWithFullSubSamples) result.getResults().get(row).getRecord();
    return sample.getFields().get(0);
  }
}
