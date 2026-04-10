package com.researchspace.service.inventory.csvexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
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

public class CsvInstrumentExporterTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("csvInstrumentExporter")
  private CsvInstrumentExporter instrumentExporter;

  @Test
  public void exportMockInstrumentToCsv() throws IOException {
    Instrument testInstrument = new Instrument();
    testInstrument.setId(7L);
    testInstrument.setName("microscope-A");
    testInstrument.setTags("optics");
    ExtraTextField textField = new ExtraTextField();
    textField.setName("serialNumber");
    textField.setData("SN-12345");
    testInstrument.addExtraField(textField);
    ExtraNumberField numberField = new ExtraNumberField();
    numberField.setName("calibration");
    numberField.setData("0.85");
    testInstrument.addExtraField(numberField);

    CsvExportMode exportMode = CsvExportMode.FULL;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    List<String> csvColumnNames =
        instrumentExporter.writeInstrumentCsvHeaderIntoOutput(
            List.of(testInstrument), exportMode, null, outputStream);
    // 5 base + 3 instrument-specific + 2 extra fields
    assertEquals(10, csvColumnNames.size());
    String expectedHeader =
        "Global ID,Name,Tags,Owner,Description,"
            + "Parent Template (Global ID),Parent Template (name),Parent Container (Global ID),"
            + "\"serialNumber (TEXT, IN7)\",\"calibration (NUMBER, IN7)\"\n";
    assertEquals(expectedHeader, outputStream.toString());

    outputStream = new ByteArrayOutputStream();
    instrumentExporter.writeInstrumentCsvDetailsIntoOutput(
        testInstrument, csvColumnNames, exportMode, null, outputStream);
    String expectedRow = "IN7,microscope-A,optics,,,,,,SN-12345,0.85\n";
    assertEquals(expectedRow, outputStream.toString());

    String csvFragment =
        instrumentExporter
            .getCsvFragmentForInstruments(List.of(testInstrument), exportMode)
            .toString();
    assertEquals(expectedHeader + expectedRow, csvFragment);
  }

  @Test
  public void emptyInstrumentListProducesEmptyOutput() throws IOException {
    String csvFragment =
        instrumentExporter.getCsvFragmentForInstruments(List.of(), CsvExportMode.FULL).toString();
    assertEquals("", csvFragment);
  }

  @Test
  public void checkInstrumentExportComment() throws IOException {
    User user = TestFactory.createAnyUser("testInstrumentExportUser");
    String csvComment =
        instrumentExporter
            .getCsvCommentFragmentForInstruments(ExportScope.USER, CsvExportMode.COMPACT, user)
            .toString();
    assertTrue(csvComment.startsWith("# " + instrumentExporter.CSV_COMMENT_HEADER), csvComment);
    assertTrue(csvComment.contains("# Exported content: INSTRUMENTS"), csvComment);
    assertTrue(csvComment.contains("# Export scope: USER"), csvComment);
    assertTrue(csvComment.contains("# Export mode: COMPACT"), csvComment);
  }
}
