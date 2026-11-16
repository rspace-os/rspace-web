package com.researchspace.maintenance.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;

class MaintenanceDeletedWindowIT extends RealTransactionSpringTestBase {
  @Autowired private MaintenanceManager maintenanceManager;

  @Test
  void deleteCommitsWhileStaleEditorWaitsToUpdate() throws Exception {
    User admin =
        createAndSaveUser(getRandomAlphabeticString("maintenance"), Constants.SYSADMIN_ROLE);
    ScheduledMaintenance stale =
        new ScheduledMaintenance(new Date(), new Date(System.currentTimeMillis() + 3600000));
    maintenanceManager.saveScheduledMaintenance(stale, admin);
    CountDownLatch editorReady = new CountDownLatch(1);
    CountDownLatch deleted = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var update =
          executor.submit(
              () -> {
                editorReady.countDown();
                assertTrue(deleted.await(10, TimeUnit.SECONDS));
                assertThrows(
                    ObjectRetrievalFailureException.class,
                    () -> maintenanceManager.updateScheduledMaintenance(stale, admin));
                return null;
              });
      assertTrue(editorReady.await(10, TimeUnit.SECONDS));
      maintenanceManager.removeScheduledMaintenance(stale.getId(), admin);
      deleted.countDown();
      update.get(15, TimeUnit.SECONDS);
      assertTrue(maintenanceManager.getAllFutureMaintenances().isEmpty());
    } finally {
      deleted.countDown();
      executor.shutdownNow();
    }
  }
}
