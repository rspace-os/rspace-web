package com.researchspace.service.inventory.csvexport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.field.ExtraNumberField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class CsvInstrumentTemplateExporterTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("csvInstrumentTemplateExporter")
  private CsvInstrumentTemplateExporter instrumentTemplateExporter;

  @Test
  public void exportMockInstrumentTemplateToCsv() throws IOException {
    InstrumentTemplate template = new InstrumentTemplate();
    template.setId(7L);
    template.setName("microscope-template");
    template.setTags("optics");
    ExtraTextField textField = new ExtraTextField();
    textField.setName("serialNumber");
    textField.setData("SN-default");
    template.addExtraField(textField);
    ExtraNumberField numberField = new ExtraNumberField();
    numberField.setName("calibration");
    numberField.setData("0.0");
    template.addExtraField(numberField);

    CsvExportMode exportMode = CsvExportMode.FULL;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    List<String> csvColumnNames =
        instrumentTemplateExporter.writeInstrumentCsvHeaderIntoOutput(
            List.of(template), exportMode, null, outputStream);
    // 5 base columns + 0 instance-only props (dropped on template) + 2 extra fields = 7
    assertEquals(7, csvColumnNames.size());
    // template-only override drops the parent-template global id from template-field column names;
    // ExtraField columns still carry the owning record's global id (here NT7).
    String expectedHeader =
        "Global ID,Name,Tags,Owner,Description,"
            + "\"serialNumber (TEXT, NT7)\",\"calibration (NUMBER, NT7)\"\n";
    assertEquals(expectedHeader, outputStream.toString());

    outputStream = new ByteArrayOutputStream();
    instrumentTemplateExporter.writeInstrumentCsvDetailsIntoOutput(
        template, csvColumnNames, exportMode, null, outputStream);
    String expectedRow = "NT7,microscope-template,optics,,,SN-default,0.0\n";
    assertEquals(expectedRow, outputStream.toString());

    String csvFragment =
        instrumentTemplateExporter
            .getCsvFragmentForInstruments(List.of(template), exportMode)
            .toString();
    assertEquals(expectedHeader + expectedRow, csvFragment);
  }

  @Test
  public void emptyTemplateListProducesEmptyOutput() throws IOException {
    String csvFragment =
        instrumentTemplateExporter
            .getCsvFragmentForInstruments(List.of(), CsvExportMode.FULL)
            .toString();
    assertEquals("", csvFragment);
  }

  @Test
  public void checkInstrumentTemplateExportComment() throws IOException {
    User user = TestFactory.createAnyUser("testInstrumentTemplateExportUser");
    String csvComment =
        instrumentTemplateExporter
            .getCsvCommentFragmentForInstrumentTemplates(
                ExportScope.USER, CsvExportMode.COMPACT, user)
            .toString();
    assertTrue(
        csvComment, csvComment.startsWith("# " + instrumentTemplateExporter.getCsvCommentHeader()));
    assertTrue(csvComment, csvComment.contains("# Exported content: INSTRUMENT_TEMPLATES"));
    assertTrue(csvComment, csvComment.contains("# Export scope: USER"));
    assertTrue(csvComment, csvComment.contains("# Export mode: COMPACT"));
  }
}
