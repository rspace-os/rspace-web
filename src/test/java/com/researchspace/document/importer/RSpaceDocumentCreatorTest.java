package com.researchspace.document.importer;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RSpaceDocumentCreatorTest {

  class DocumentImporterFromWord2HTMLTSS extends DocumentImporterFromWord2HTML {
    StructuredDocument extractAndAddImages(
        ContentProvider provider,
        Folder targetFolder,
        Folder imageFolder,
        String docName,
        User creator,
        File contentFolder) {
      return null;
    }
  }

  @Mock ContentProvider provider;
  private @Mock IPermissionUtils permUtils;
  User anyUser;
  @InjectMocks RSpaceDocumentCreator creator = new DocumentImporterFromWord2HTMLTSS();
  File anyFile = RSpaceTestUtils.getResource("word2rspace/dsRNAi/dsRNAi-in-Drosophila-cells.html");

  @BeforeEach
  public void before() {
    anyUser = TestFactory.createAnyUser("any");
  }

  @Test
  public void imageFolderValidation() throws IOException {
    Folder targetFolder = TestFactory.createAFolder("workspace", anyUser);
    Folder nonImgFolder = TestFactory.createAFolder("workspace", anyUser);
    assertThrows(
        IllegalArgumentException.class,
        () -> creator.create(provider, targetFolder, nonImgFolder, "something.doc", anyUser));
    nonImgFolder.addType(RecordType.SYSTEM);

    assertThrows(
        IllegalArgumentException.class,
        () -> creator.create(provider, targetFolder, nonImgFolder, "something.doc", anyUser));
    nonImgFolder.setName(MediaUtils.IMAGES_MEDIA_FLDER_NAME);
    Mockito.when(provider.getContentFolder()).thenReturn(anyFile);
    creator.create(provider, targetFolder, nonImgFolder, "something.doc", anyUser);
  }
}
