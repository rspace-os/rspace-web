package com.researchspace.service.dmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DmpSourceDetectorTest {

  private static final String FIXTURES = "src/test/resources/TestResources/dmpSources/";

  private @Mock FileStore fileStore;
  private DmpSourceDetector detector;
  private DMPUser plan;
  private FileProperty fileProperty;

  @BeforeEach
  void setUp() {
    detector = new DmpSourceDetector(fileStore);
    plan =
        new DMPUser(
            TestFactory.createAnyUser("any"),
            new DmpDto("1", "A plan", DMPSource.UNKNOWN, null, null));
    fileProperty = new FileProperty();
    EcatDocumentFile file = new EcatDocumentFile();
    file.setFileProperty(fileProperty);
    plan.setDmpDownloadFile(file);
  }

  @Test
  void pdfIsDmpTool() throws Exception {
    givenFile("dmptool-plan.pdf");

    assertEquals(DMPSource.DMP_TOOL, detector.detect(plan));
  }

  @ParameterizedTest
  @CsvSource({
    "dmptool-plan.json, DMP_TOOL",
    "dmponline-plan.json, DMP_ONLINE",
    "dmponline-plan-list.json, DMP_ONLINE",
    "argos-plan.json, ARGOS",
  })
  void jsonLayoutGivesItsPlatform(String fileName, DMPSource expected) throws Exception {
    givenFile(fileName);

    assertEquals(expected, detector.detect(plan));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"unrecognised-plan.json", "invalid-plan.json", "empty-plan.json", "missing.json"})
  void fileOfNoKnownLayoutIsUnknown(String fileName) throws Exception {
    givenFile(fileName);

    assertEquals(DMPSource.UNKNOWN, detector.detect(plan));
  }

  @Test
  void planWithoutStoredFileIsUnknown() {
    plan.setDmpDownloadFile(null);

    assertEquals(DMPSource.UNKNOWN, detector.detect(plan));
    verifyNoInteractions(fileStore);
  }

  @Test
  void fileStoreFailureIsUnknown() throws Exception {
    when(fileStore.findFile(fileProperty)).thenThrow(new IllegalStateException("store down"));

    assertEquals(DMPSource.UNKNOWN, detector.detect(plan));
  }

  private void givenFile(String name) throws Exception {
    when(fileStore.findFile(fileProperty)).thenReturn(new File(FIXTURES + name));
  }
}
