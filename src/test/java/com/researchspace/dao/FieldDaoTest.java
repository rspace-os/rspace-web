package com.researchspace.dao;

// import org.compass.core.CompassTemplate;
// import org.compass.gps.CompassGps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.EcatImage;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

public class FieldDaoTest extends SpringTransactionalTest {

  @Autowired private FieldDao dao;

  @Test
  public void testGetFieldInvalid() {
    assertThrows(DataAccessException.class, () -> dao.get(1000L));
  }

  @Test
  public void testgetFieldByContent() {
    User u = createAndSaveUserIfNotExists("xasas");
    initialiseContentWithEmptyContent(u);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(u, "text");

    assertThat(dao.findByTextContent("tex")).hasSize(1);
    assertThat(dao.findByTextContent("ext")).hasSize(1);
  }

  @Test
  public void deleteFieldAttachmentDeletesTrackedAttachment() throws IOException {
    FieldAttachment fa = createAndUnlinkFieldAttachment();
    Long id = fa.getId();
    dao.deleteFieldAttachment(fa);
    assertAttachmentDeleted(id);
  }

  @Test
  public void deleteFieldAttachmentDeletesDetachedAttachment() throws IOException {
    FieldAttachment fa = createAndUnlinkFieldAttachment();
    Long id = fa.getId();
    sessionFactory.getCurrentSession().evict(fa);
    dao.deleteFieldAttachment(fa);
    assertAttachmentDeleted(id);
  }

  /** Mirrors FieldLinksEntitySyncImpl: the attachment is taken out of the field before deletion. */
  private FieldAttachment createAndUnlinkFieldAttachment() throws IOException {
    User u = createAndSaveUserIfNotExists(getRandomAlphabeticString("fa"));
    initialiseContentWithEmptyContent(u);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(u, "text");
    Field field = sd.getFields().get(0);
    EcatImage image = addImageToField(field, u);
    flushDatabaseState();
    return field.removeMediaFileLink(image).get();
  }

  private void assertAttachmentDeleted(Long id) {
    flushDatabaseState();
    sessionFactory.getCurrentSession().clear();
    assertNull(sessionFactory.getCurrentSession().get(FieldAttachment.class, id));
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }
}
