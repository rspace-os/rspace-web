package com.researchspace.service;

import static com.researchspace.testutils.CommsTestUtils.createRequestOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.CommunicationDao;
import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.MessageType;
import com.researchspace.service.impl.RSpaceRequestManagerImpl;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class RSpaceRequestManagerTest extends SpringTransactionalTest {

  private RSpaceRequestManagerImpl requestMgr = new RSpaceRequestManagerImpl();

  private CommunicationDao mockCommDao;

  private User user;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    user = TestFactory.createAnyUser("any");
    user = userMgr.save(user);

    mockCommDao = mock(CommunicationDao.class);
    requestMgr.setCommDao(mockCommDao);
    // built with new(), so the @Autowired message source needs wiring by hand
    ReflectionTestUtils.setField(
        requestMgr, "messages", new MessageSourceUtils(new JsonMessageSource()));
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void cannotReplyToGlobalMessage_RSPAC_1460() {

    Communication globalMsg = createRequestOfType(user, MessageType.GLOBAL_MESSAGE);
    when(mockCommDao.get(1L)).thenReturn(globalMsg);
    assertThrows(IllegalStateException.class, () -> requestMgr.replyToMessage("user", 1L, "reply"));
  }
}
