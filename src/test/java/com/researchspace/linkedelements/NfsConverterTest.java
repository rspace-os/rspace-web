package com.researchspace.linkedelements;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.netfiles.NfsElement;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NfsConverterTest extends AbstractParserTest {

  private int NFS_ID = 62;
  private String FILE_PATH = "/readme.txt";
  private String DIR_PATH = "/docs";

  NfsConverter nfsConverter;

  @Before
  public void setUp() throws Exception {
    nfsConverter = new NfsConverter();
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void jsoup2NfsLinkableElement() {
    String nfsFileLink = rtu.generateURLStringForNfs((long) NFS_ID, FILE_PATH, false);
    Element jsoupFile = createJSoupNfsLink(nfsFileLink);
    String nfsFolderLink = rtu.generateURLStringForNfs((long) NFS_ID, DIR_PATH, true);
    Element jsoupFolder = createJSoupNfsLink(nfsFolderLink);

    FieldContents fieldContents = new FieldContents();
    nfsConverter.jsoup2LinkableElement(fieldContents, jsoupFile);
    nfsConverter.jsoup2LinkableElement(fieldContents, jsoupFolder);

    FieldElementLinkPairs<NfsElement> elements = fieldContents.getElements(NfsElement.class);
    assertNotNull(elements);
    assertEquals(2, elements.size());
    NfsElement fileElement = elements.getElements().get(0);
    assertEquals(NFS_ID, fileElement.getFileStoreId().intValue());
    assertEquals(FILE_PATH, fileElement.getPath());
    assertFalse(fileElement.isFolderLink());
    NfsElement dirElement = elements.getElements().get(1);
    assertEquals(NFS_ID, dirElement.getFileStoreId().intValue());
    assertEquals(DIR_PATH, dirElement.getPath());
    assertTrue(dirElement.isFolderLink());
  }

  private Element createJSoupNfsLink(String nfsLink) {
    return Jsoup.parse("<html>" + nfsLink + "</html>").select("a").first();
  }

  @Test()
  public void jsoup2NfsLinkableElementMissingRelElementHandled() {
    String nfsLink = "<a class=\"nfs_file mceNonEditable\" data-linktype=\"file\" href=\"#\">";
    Element jsoup = createJSoupNfsLink(nfsLink);
    FieldContents fieldContents = new FieldContents();
    Optional<NfsElement> fieldElement = nfsConverter.jsoup2LinkableElement(fieldContents, jsoup);
    assertNoElement(fieldContents, fieldElement);
  }

  @Test()
  public void jsoup2NfsLinkableElementRelElementHandled() {
    String nfsLink =
        "<a class=\"nfs_file mceNonEditable\" data-linktype=\"file\" rel=\"wrong:/readme.txt\""
            + " href=\"#\">";
    Element jsoup = createJSoupNfsLink(nfsLink);
    FieldContents fieldContents = new FieldContents();
    Optional<NfsElement> fieldElement = nfsConverter.jsoup2LinkableElement(fieldContents, jsoup);
    assertNoElement(fieldContents, fieldElement);
  }

  @Test
  public void jsoup2NfsLinkableElementRichTextUpdaterRoundTrip() {
    String nfsLink = rtu.generateURLStringForNfs((long) NFS_ID, FILE_PATH, false);
    Element jsoup = createJSoupNfsLink(nfsLink);
    FieldContents fieldContents = new FieldContents();
    Optional<NfsElement> fieldElement = nfsConverter.jsoup2LinkableElement(fieldContents, jsoup);
    assertEquals(NFS_ID, fieldElement.get().getFileStoreId().intValue());
    assertEquals(FILE_PATH, fieldElement.get().getPath());
  }

  private void assertNoElement(FieldContents fieldContents, Optional<NfsElement> fieldElement) {
    assertFalse(fieldElement.isPresent());
    assertEquals(0, fieldContents.getElements(NfsElement.class).size());
  }
}
