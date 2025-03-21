package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SharingHandlerTest extends SpringTransactionalTest {

  private @Autowired SharingHandler sharingHandler;

  @Test
  public void shareIntoSharedFolder() {
    User admin = createAndSaveAPi();
    initialiseContentWithEmptyContent(admin);
    logoutAndLoginAs(admin);
    RSForm form = formMgr.create(admin);

    User user = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    Group group = new Group(CoreTestUtils.getRandomName(10), admin);
    group = grpMgr.saveGroup(group, user);
    initialiseContentWithEmptyContent(user, admin);

    group = grpMgr.addUserToGroup(admin.getUsername(), group.getId(), RoleInGroup.PI);
    Folder sharedFolder =
        grpMgr.createSharedCommunalGroupFolders(group.getId(), admin.getUsername());
    flushDatabaseState();

    Record newDoc =
        recordMgr.createNewStructuredDocument(admin.getRootFolder().getId(), form.getId(), admin);
    assertFalse(newDoc.isShared());
    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> sharingResult =
        sharingHandler.shareIntoSharedFolder(admin, sharedFolder, newDoc.getId());
    newDoc = recordMgr.get(newDoc.getId());
    assertTrue(newDoc.isShared());
  }
}
