package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class InternalFileStoreIT extends RealTransactionSpringTestBase {

  @Autowired private InternalFileStore internalFileStore;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  void failedStreamWriteRollsBackPersistedMetadata() throws IOException {
    assertTrue(AopUtils.isAopProxy(internalFileStore));
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    internalFileStore.setupInternalFileStoreRoot();
    FileProperty property = new FileProperty();
    property.setFileUser(piUser.getUsername());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    try (InputStream input =
        new InputStream() {
          @Override
          public int read() throws IOException {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(property.getId() != null);
            sessionFactory.getCurrentSession().flush();
            throw new IOException("source failed after metadata was persisted");
          }
        }) {
      assertThrows(
          IOException.class,
          () ->
              internalFileStore.save(
                  property, input, "rollback.txt", FileDuplicateStrategy.AS_NEW));
    }
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from FileProperty where id = ?", Integer.class, property.getId()));
    assertFalse(internalFileStore.findFile(property).exists());
  }
}
