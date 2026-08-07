package com.researchspace.document.importer;

import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.linkedelements.FieldContents;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class EvernoteEnexImporterTest extends SpringTransactionalTest {
  private static final String EVERNOTE_DUMP_ENEX = "EvernoteDump.enex";
  private static final int EVERNOTE_DUMP_ENEX_NOTE_COUNT = 5;

  @Qualifier("evernoteFileImporter")
  @Autowired
  ExternalFileImporter evernoteFileImporter;

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testImportWithAllMediaAttachments() throws IOException {
    User anyUser = createInitAndLoginAnyUser();
    InputStream is = RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder(EVERNOTE_DUMP_ENEX);
    Folder target = anyUser.getRootFolder();

    final Long initialMediaCount = getMediaCount(anyUser);
    BaseRecord folder = evernoteFileImporter.create(is, anyUser, target, null, EVERNOTE_DUMP_ENEX);
    is.close();
    assertNotNull(folder);
    assertTrue(folder.isFolder());
    // update
    folder = folderMgr.getFolder(folder.getId(), anyUser);
    assertEquals(getBaseName(EVERNOTE_DUMP_ENEX), folder.getName());
    assertEquals(EVERNOTE_DUMP_ENEX_NOTE_COUNT, folder.getChildrens().size());
    assertTrue(allCreatedDocsAreBasicDocuments(folder));
    StructuredDocument doc = getImportedNote1(folder);
    assertEquals(2, doc.getDocTag().split(",").length);

    final int EXPECTED_ATTACH_COUNT = 7;

    // note 1 has 2 images , video, audio and 3 documents inserted.
    FieldContents contents = fieldParser.findFieldElementsInContent(doc.getFirstFieldData());
    assertEquals(EXPECTED_ATTACH_COUNT, contents.getAllMediaFiles().size());

    assertEquals(2, contents.getMediaElements(EcatImage.class).size());
    assertEquals(1, contents.getMediaElements(EcatAudio.class).size());
    assertEquals(1, contents.getMediaElements(EcatVideo.class).size());
    assertEquals(2, contents.getMediaElements(EcatDocumentFile.class).size());
    assertEquals(1, contents.getMediaElements(EcatChemistryFile.class).size());
    assertEquals(initialMediaCount + EXPECTED_ATTACH_COUNT, getMediaCount(anyUser).intValue());
  }

  private StructuredDocument getImportedNote1(BaseRecord folder) {
    return folder.getChildrens().stream()
        .filter(br -> "note1".equals(br.getName()))
        .findFirst()
        .get()
        .asStrucDoc();
  }

  private boolean allCreatedDocsAreBasicDocuments(BaseRecord folder) {
    return folder.getChildrens().stream()
        .map(BaseRecord::asStrucDoc)
        .map(StructuredDocument::getForm)
        .allMatch(form -> form.getName().equals("Basic Document"));
  }
}
