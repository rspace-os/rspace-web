package com.researchspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.dao.GroupDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.interceptor.TransactionInterceptor;

public class LabGroupStorageAlertManagerDbTest extends SpringTransactionalTest {

  private @Autowired LabGroupStorageAlertManager storageAlertManager;
  private @Autowired GroupDao groupDao;
  private @Autowired FileMetadataDao fileMetadataDao;
  private @Autowired CommunicationManager communicationManager;

  @Test
  public void alertIsOffUntilAThresholdIsSet() {
    assertEquals(Optional.empty(), storageAlertManager.getThresholdMb());
  }

  @Test
  public void managerMethodsRunUnderTheManagerTransactionAdvice() {
    Advised proxy = assertInstanceOf(Advised.class, storageAlertManager);
    assertThat(proxy.getAdvisors())
        .extracting(Advisor::getAdvice)
        .hasAtLeastOneElementOfType(TransactionInterceptor.class);
  }

  @Test
  public void settingAThresholdAlertsSysadminsAboutAGroupAtOrAboveIt() {
    User sysadmin = logoutAndLoginAsSysAdmin();
    User pi = createAndSaveAPi();
    initialiseContentWithEmptyContent(pi);
    Group group = addUsersToGroup(pi, createGroup(getRandomAlphabeticString("storage"), pi));
    FileProperty file = new FileProperty();
    file.setFileSize("3000000");
    file.setRelPath("any");
    file.setFileOwner(pi.getUsername());
    fileMetadataDao.save(file);
    flushDatabaseState();

    try {
      assertEquals(Optional.of(2L), storageAlertManager.setThresholdMb("2", sysadmin));

      assertEquals(Optional.of(2L), storageAlertManager.getThresholdMb());
      assertThat(groupDao.getStorageAlertSentGroupIds()).contains(group.getId());
      ISearchResults<Notification> received =
          communicationManager.getNewNotificationsForUser(
              sysadmin.getUsername(),
              PaginationCriteria.createDefaultForClass(CommunicationTarget.class)
                  .setGetAllResults());
      assertThat(received.getResults())
          .anySatisfy(
              n -> {
                assertEquals(NotificationType.LAB_GROUP_STORAGE_ALERT, n.getNotificationType());
                assertThat(n.getNotificationMessage())
                    .contains(group.getDisplayName(), "threshold of 2 MB");
              });
    } finally {
      storageAlertManager.setThresholdMb(null, sysadmin);
    }
  }
}
