package com.researchspace.dao;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.TestFactory;
import com.researchspace.search.impl.FileIndexSearcher;
import com.researchspace.search.impl.LuceneSearchStrategy;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class EcatDocumentFileDaoTest extends SpringTransactionalTest {

  private static final String KNOWN_FILE = "genFilesi.txt";
  private static final String KNOWN_FILE2 = "RSLogs.txt";

  private static final String UNKNOWN_FILE = "XXXXXX.txt";

  private @Autowired EcatDocumentFileDao docFileDao;
  private @Autowired FileMetadataDao fDao;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetEcatDocumentFileByURI() throws IOException {
    User any = createAndSaveRandomUser();
    FileStoreRoot root = fileStore.getCurrentFileStoreRoot();

    EcatDocumentFile doc = TestFactory.createEcatDocument(1L, any);

    File actualFile = RSpaceTestUtils.getResource(KNOWN_FILE);
    FileProperty fp = TestFactory.createAFileProperty(actualFile, any, root);
    URI savedLocation =
        fileStore.save(
            fp, new FileInputStream(actualFile), KNOWN_FILE, FileDuplicateStrategy.REPLACE);

    doc.setFileProperty(fp);
    doc.setName(fp.getFileName());
    docFileDao.save(doc);
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    ISearchResults<BaseRecord> isr =
        docFileDao.getEcatDocumentFileByURI(
            pgCrit, any.getUsername(), toList(absToRel(savedLocation.toString())));
    assertEquals(1, isr.getTotalHits().intValue());
    assertEquals(doc, isr.getFirstResult());

    String unknownLocation = savedLocation.toString().replace(KNOWN_FILE, UNKNOWN_FILE);
    isr = docFileDao.getEcatDocumentFileByURI(pgCrit, any.getUsername(), toList(unknownLocation));
    assertEquals(0, isr.getTotalHits().intValue());
    assertTrue(isr.getResults().isEmpty());

    EcatDocumentFile doc2 = TestFactory.createEcatDocument(2L, any);
    File actualFile2 = RSpaceTestUtils.getResource(KNOWN_FILE2);
    FileProperty fp2 = TestFactory.createAFileProperty(actualFile2, any, root);
    URI savedLocation2 =
        fileStore.save(
            fp2, new FileInputStream(actualFile2), KNOWN_FILE2, FileDuplicateStrategy.REPLACE);
    doc2.setFileProperty(fp2);
    doc2.setName(fp2.getFileName());
    doc2 = docFileDao.save(doc2);

    pgCrit.setOrderBy("name");
    pgCrit.setSortOrder(SortOrder.ASC);
    isr =
        docFileDao.getEcatDocumentFileByURI(
            pgCrit,
            any.getUsername(),
            toList(absToRel(savedLocation.toString()), absToRel(savedLocation2.toString())));
    assertEquals(2, isr.getTotalHits().intValue());
    assertEquals(doc, isr.getFirstResult());

    pgCrit.setSortOrder(SortOrder.DESC);
    isr =
        docFileDao.getEcatDocumentFileByURI(
            pgCrit,
            any.getUsername(),
            toList(absToRel(savedLocation.toString()), absToRel(savedLocation2.toString())));
    assertEquals(2, isr.getTotalHits().intValue());
    assertEquals(doc2, isr.getFirstResult());
  }

  String absToRel(String absPath) {
    return FileIndexSearcher.removeFileSeparators(LuceneSearchStrategy.absPathToRelPath(absPath));
  }
}
