package com.researchspace.service.inventory.csvimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryImportInstrumentImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportPartialResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSubSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.model.User;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A coded exception that fails a CSV row is reported with its catalogue text, not its bundle key
 * (RSDEV-1521). Import is all-or-nothing, so this text is the user's only feedback.
 */
public class CsvImporterRowErrorMessageTest {

  private static final String LINK_CELL = "Cites https://rspace.example.com/globalId/SA1";
  private static final String RELATION_NOT_PERMITTED =
      "Relation type 'Cites' is not permitted for field 'Derived from'.";
  private static final String UNPARSABLE_QUANTITY = "Could not parse quantity [lots]";

  private final MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
  private final User user = new User("importer");

  @Test
  void sampleRowReportsApiRuntimeExceptionText() {
    CsvSampleImporter importer = new CsvSampleImporter();
    importer.messages = messages;
    importer.linkParser = linkParserRefusingRelation();
    ApiSampleTemplate template = new ApiSampleTemplate();
    template.getFields().add(linkField());
    ApiInventoryImportSampleImportResult result = new ApiInventoryImportSampleImportResult();
    result.addCreatedTemplateResult(template);

    importer.convertLinesToSamples(
        result, List.<String[]>of(new String[] {"s1", LINK_CELL}), Map.of(0, "name"), 2, user);

    assertEquals(RELATION_NOT_PERMITTED, result.getResults().get(0).getError().getErrors().get(0));
  }

  @Test
  void instrumentRowReportsApiRuntimeExceptionText() {
    CsvInstrumentImporter importer = new CsvInstrumentImporter();
    importer.messages = messages;
    importer.linkParser = linkParserRefusingRelation();
    ApiInstrumentTemplate template = new ApiInstrumentTemplate();
    template.getFields().add(linkField());
    ApiInventoryImportInstrumentImportResult result =
        new ApiInventoryImportInstrumentImportResult();
    result.addCreatedTemplateResult(template);

    importer.convertLinesToInstruments(
        result, List.<String[]>of(new String[] {"i1", LINK_CELL}), Map.of(0, "name"), 2, user);

    assertEquals(RELATION_NOT_PERMITTED, result.getResults().get(0).getError().getErrors().get(0));
  }

  @Test
  void subSampleRowReportsLocalizedExceptionText() {
    CsvSubSampleImporter importer = new CsvSubSampleImporter();
    importer.messages = messages;
    ApiInventoryImportSubSampleImportResult result = new ApiInventoryImportSubSampleImportResult();

    importer.convertLinesToSubSamples(
        result,
        List.<String[]>of(new String[] {"ss1", "lots"}),
        Map.of(0, "name", 1, "quantity"),
        2,
        user);

    assertEquals(UNPARSABLE_QUANTITY, result.getResults().get(0).getError().getErrors().get(0));
  }

  @Test
  void containerRowReportsLocalizedExceptionText() {
    CsvContainerImporter importer = new CsvContainerImporter();
    importer.messages = messages;
    ApiInventoryImportPartialResult result =
        new ApiInventoryImportPartialResult(ApiInventoryRecordType.CONTAINER);

    importer.convertLinesToContainers(
        result,
        List.<String[]>of(new String[] {"c1", "lots"}),
        Map.of(0, "name", 1, "quantity"),
        2,
        user);

    assertEquals(UNPARSABLE_QUANTITY, result.getResults().get(0).getError().getErrors().get(0));
  }

  private CsvLinkValueParser linkParserRefusingRelation() {
    CsvLinkValueParser parser = mock(CsvLinkValueParser.class);
    when(parser.parse(eq(LINK_CELL), any()))
        .thenThrow(
            new ApiRuntimeException(
                "errors.inventory.field.linkRelationTypeNotPermitted", "Cites", "Derived from"));
    return parser;
  }

  private static ApiInventoryEntityField linkField() {
    ApiInventoryEntityField field = new ApiInventoryEntityField();
    field.setName("Derived from");
    field.setType(ApiFieldType.LINK);
    return field;
  }
}
