package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalStateExceptionThrown;
import static com.researchspace.model.comms.CommsTestUtils.createRequestOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.CommunicationDao;
import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.impl.RSpaceRequestManagerImpl;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RSpaceRequestManagerTest extends SpringTransactionalTest {

  private RSpaceRequestManagerImpl requestMgr = new RSpaceRequestManagerImpl();

  private CommunicationDao mockCommDao;

  private User user;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = TestFactory.createAnyUser("any");
    user = userMgr.save(user);

    mockCommDao = mock(CommunicationDao.class);
    requestMgr.setCommDao(mockCommDao);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void cannotReplyToGlobalMessage_RSPAC_1460() {

    Communication globalMsg = createRequestOfType(user, MessageType.GLOBAL_MESSAGE);
    when(mockCommDao.get(1L)).thenReturn(globalMsg);
    assertIllegalStateExceptionThrown(() -> requestMgr.replyToMessage("user", 1L, "reply"));
  }
}
