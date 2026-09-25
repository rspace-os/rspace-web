package com.researchspace.service.inventory.csvexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.junit.jupiter.api.Test;
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
    assertThat(csvColumnNames).hasSize(10);
    String expectedHeader =
        "Global ID,Name,Tags,Owner,Description,"
            + "Parent Template (Global ID),Parent Template (name),Parent Container (Global ID),"
            + "\"serialNumber (TEXT, IN7)\",\"calibration (NUMBER, IN7)\"\n";
    assertThat(outputStream).hasToString(expectedHeader);

    outputStream = new ByteArrayOutputStream();
    instrumentExporter.writeInstrumentCsvDetailsIntoOutput(
        testInstrument, csvColumnNames, exportMode, null, outputStream);
    String expectedRow = "IN7,microscope-A,optics,,,,,,SN-12345,0.85\n";
    assertThat(outputStream).hasToString(expectedRow);

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
    assertThat(csvFragment).isEmpty();
  }

  @Test
  public void checkInstrumentExportComment() throws IOException {
    User user = TestFactory.createAnyUser("testInstrumentExportUser");
    String csvComment =
        instrumentExporter
            .getCsvCommentFragmentForInstruments(ExportScope.USER, CsvExportMode.COMPACT, user)
            .toString();
    assertThat(csvComment)
        .as(csvComment)
        .startsWith("# " + instrumentExporter.getCsvCommentHeader());
    assertThat(csvComment).as(csvComment).contains("# Exported content: INSTRUMENTS");
    assertThat(csvComment).as(csvComment).contains("# Export scope: USER");
    assertThat(csvComment).as(csvComment).contains("# Export mode: COMPACT");
    assertThat(csvComment).as(csvComment).contains("# Export mode: COMPACT");
  }
}
