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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class InternalFileStoreIT extends RealTransactionSpringTestBase {

  @Autowired private InternalFileStore internalFileStore;

  @Test
  void failedStreamWriteRollsBackPersistedMetadata() throws IOException {
    assertTrue(AopUtils.isAopProxy(internalFileStore));
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    internalFileStore.setupInternalFileStoreRoot();
    FileProperty property = new FileProperty();
    property.setFileUser(UUID.randomUUID().toString());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    try (InputStream input =
        new InputStream() {
          @Override
          public int read() throws IOException {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            sessionFactory.getCurrentSession().flush();
            assertEquals(
                1,
                jdbc.queryForObject(
                    "select count(*) from FileProperty where id = ?",
                    Integer.class,
                    property.getId()));
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
