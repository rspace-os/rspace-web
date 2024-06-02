package com.researchspace.document.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatImage;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class WordHTML2RspaceTest extends SpringTransactionalTest {

  File wordHtml;
  File word2rspaceFolder;
  @Autowired RichTextUpdater updater;
  @Autowired RSpaceDocumentCreator creator;
  final int NUM_IMAGES_IN_HTML = 3;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    wordHtml = RSpaceTestUtils.getResource("word2rspace/powerpaste/PowerPasteTesting_RSpace.html");
    word2rspaceFolder = wordHtml.getParentFile();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void mediaFilesAreImported() throws Exception {
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    int initialMediaCount = getMediaCount(any).intValue();
    logoutAndLoginAs(any);
    Folder root = folderDao.getRootRecordForUser(any);
    HTMLContentProvider contentProvider = new HTMLContentProvider(word2rspaceFolder, wordHtml);
    BaseRecord created = creator.create(contentProvider, root, null, wordHtml.getName(), any);
    int finalMediaCount = getMediaCount(any).intValue();
    assertEquals(NUM_IMAGES_IN_HTML, finalMediaCount - initialMediaCount);

    String text = getBasicDocumentText(created);
    FieldContents contents = fieldParser.findFieldElementsInContent(text);
    assertEquals(3, contents.getElements(EcatImage.class).size());
  }

  @Test
  public void imageFilesCreatedFromWordCanBeReadBySharees() throws Exception {
    User pi = createAndSaveAPi();
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(pi, any);
    logoutAndLoginAs(pi);
    Group grp = createGroupForUsers(pi, pi.getUsername(), "", pi, any);

    Folder root = folderDao.getRootRecordForUser(pi);
    HTMLContentProvider contentProvider = new HTMLContentProvider(word2rspaceFolder, wordHtml);
    String docname = FilenameUtils.getBaseName(wordHtml.getName());
    BaseRecord created = creator.create(contentProvider, root, null, docname, pi);
    assertEquals(docname, created.getName());
    shareRecordWithGroup(pi, grp, created.asStrucDoc());

    // now login as sharee, check has permission to view the images
    logoutAndLoginAs(any);
    StructuredDocument doc = recordMgr.get(created.getId()).asStrucDoc();
    List<EcatImage> images = getImagesFromTextField(doc);
    for (EcatImage img : images) {
      assertTrue(permissionUtils.isPermittedViaMediaLinksToRecords(img, PermissionType.READ, any));
    }
  }

  private List<EcatImage> getImagesFromTextField(StructuredDocument doc) {
    return fieldParser
        .findFieldElementsInContent(getBasicDocumentText(doc))
        .getElements(EcatImage.class)
        .getElements();
  }

  private String getBasicDocumentText(BaseRecord created) {
    return created.asStrucDoc().getFields().get(0).getFieldData();
  }
}
