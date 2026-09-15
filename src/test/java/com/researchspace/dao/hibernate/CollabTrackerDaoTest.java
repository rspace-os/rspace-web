package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.dao.CollaborationGroupTrackerDao;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.model.CollabGroupCreationTracker;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CollabTrackerDaoTest extends SpringTransactionalTest {

  @Autowired CollaborationGroupTrackerDao trackerDao;

  @Autowired CommunicationDao commDao;

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testCreateSaveAndReloadTracker() {
    // set up dependent objects
    MessageOrRequest anyMor = new MessageOrRequest(MessageType.REQUEST_EXTERNAL_SHARE);
    User anyUser = createAndSaveUserIfNotExists("any");
    anyMor.setOriginator(anyUser);
    commDao.save(anyMor);

    // create and save tracker
    CollabGroupCreationTracker tracker = new CollabGroupCreationTracker();
    tracker.setMor(anyMor);
    tracker.setNumInvitations((short) 2);
    trackerDao.save(tracker);

    // reoad using message Id
    CollabGroupCreationTracker loaded = trackerDao.getByRequestId(anyMor);
    assertNotNull(loaded);
  }
}
