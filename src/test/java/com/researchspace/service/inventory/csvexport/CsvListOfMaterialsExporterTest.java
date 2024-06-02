package com.researchspace.service.inventory.csvexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraNumberField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CsvListOfMaterialsExporterTest extends SpringTransactionalTest {

  @Autowired private CsvListOfMaterialsExporter lomExporter;

  @Test
  public void exportMockListOfMaterialsToCsv() throws IOException {

    // prepare test container
    Container testContainer = new Container();
    testContainer.setId(5L);
    testContainer.setName("testContainer");
    testContainer.setTags("testTags");
    ExtraTextField textField = new ExtraTextField();
    textField.setName("test extra field");
    textField.setData("test data");
    testContainer.addExtraField(textField);

    // prepare test subsample
    SubSample testSubSample = new SubSample();
    testSubSample.setId(15L);
    testSubSample.setName("testSubSample");
    testSubSample.setQuantity(QuantityInfo.of(5, RSUnitDef.MILLI_GRAM));
    testSubSample.setSample(new Sample());
    testSubSample.getSample().setOwner(new User("testOwner"));
    ExtraNumberField numberField = new ExtraNumberField();
    numberField.setName("Test Numeric");
    numberField.setData("515.15");
    testSubSample.addExtraField(numberField);

    // prepare test lom
    ListOfMaterials testLom = new ListOfMaterials();
    testLom.setId(3L);
    testLom.setName("testLom");
    testLom.addMaterial(testContainer, null);
    testLom.addMaterial(testSubSample, QuantityInfo.of(2, RSUnitDef.MILLI_GRAM));

    // run the export
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    List<String> csvColumnNames = lomExporter.writeLomCsvHeaderIntoOutput(null, outputStream);
    assertEquals(6, csvColumnNames.size());
    String csvHeaderLineForListOfMaterials = outputStream.toString();
    String expectedColumnNamesLine =
        "List of Materials (Global ID),List of Materials (Name),Used Material (Global ID),"
            + "Used Material (Name),Used Material (Type),Used Quantity\n";
    assertEquals(expectedColumnNamesLine, csvHeaderLineForListOfMaterials);

    outputStream = new ByteArrayOutputStream();
    lomExporter.writeMaterialUsageCsvDetailsIntoOutput(
        testLom, testLom.getMaterials().get(0), null, outputStream);
    String csvLineForMaterialUsage = outputStream.toString();
    String expectedMaterialUsageLine = "LM3,testLom,IC5,testContainer,CONTAINER,\n";
    assertEquals(expectedMaterialUsageLine, csvLineForMaterialUsage);

    outputStream = new ByteArrayOutputStream();
    lomExporter.writeMaterialUsageCsvDetailsIntoOutput(
        testLom, testLom.getMaterials().get(1), null, outputStream);
    csvLineForMaterialUsage = outputStream.toString();
    String expectedMaterialUsageLine2 = "LM3,testLom,SS15,testSubSample,SUBSAMPLE,2 mg\n";
    assertEquals(expectedMaterialUsageLine2, csvLineForMaterialUsage);

    String csvFragmentForListOfMaterials =
        lomExporter.getCsvFragmentForListsOfMaterials(List.of(testLom)).toString();
    assertEquals(
        expectedColumnNamesLine + expectedMaterialUsageLine + expectedMaterialUsageLine2,
        csvFragmentForListOfMaterials);
  }

  @Test
  public void checkLomExportComment() throws IOException {
    User user = TestFactory.createAnyUser("testExportUser");
    String csvComment =
        lomExporter
            .getCsvCommentFragmentForLom(ExportScope.SELECTION, CsvExportMode.COMPACT, user)
            .toString();
    assertTrue(csvComment.startsWith("# " + lomExporter.CSV_COMMENT_HEADER), csvComment);
    assertTrue(csvComment.contains("# Exported content: LIST_OF_MATERIALS"), csvComment);
    assertTrue(csvComment.contains("# Export scope: SELECTION"), csvComment);
    assertTrue(csvComment.contains("# Export mode: COMPACT"), csvComment);
  }
}
