package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserConnectionDaoTest extends SpringTransactionalTest {

  private static final String PROVIDER_NAME = "egnyte";
  private static final String RSPACEUSERNAME = "rspaceusername";
  private @Autowired UserConnectionDao userConnectionDao;

  @Test
  public void createSaveAndFindAndDelete() {
    int initialCount = userConnectionDao.getAllDistinct().size();
    UserConnectionId id = new UserConnectionId(RSPACEUSERNAME, PROVIDER_NAME, "userEgnyteId");
    UserConnection conn = new UserConnection(id, "accessToken");
    conn.setRefreshToken("refreshToken");
    userConnectionDao.save(conn);
    assertEquals(initialCount + 1, userConnectionDao.getAllDistinct().size());

    UserConnection retrieved = userConnectionDao.get(id);
    assertEquals(conn, retrieved);

    assertFalse(
        userConnectionDao.findByUserNameProviderName("unknownUser", "unknownProvider").isPresent());
    assertTrue(
        userConnectionDao.findByUserNameProviderName(RSPACEUSERNAME, PROVIDER_NAME).isPresent());

    assertEquals(1, userConnectionDao.deleteByUserAndProvider(PROVIDER_NAME, RSPACEUSERNAME));
    assertEquals(initialCount, userConnectionDao.getAllDistinct().size());
  }
}
