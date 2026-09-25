package com.researchspace.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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
    assertThat(userConnectionDao.getAllDistinct()).hasSize(initialCount + 1);

    UserConnection retrieved = userConnectionDao.get(id);
    assertEquals(conn, retrieved);

    assertThat(userConnectionDao.findByUserNameProviderName("unknownUser", "unknownProvider"))
        .isNotPresent();
    assertThat(userConnectionDao.findByUserNameProviderName(RSPACEUSERNAME, PROVIDER_NAME))
        .isPresent();

    assertEquals(1, userConnectionDao.deleteByUserAndProvider(RSPACEUSERNAME, PROVIDER_NAME));
    assertThat(userConnectionDao.getAllDistinct()).hasSize(initialCount);
  }

  @Test
  public void createSaveAndFindAndDeleteWithDiscriminant() {
    final String DISCRIMINANT = "discriminant";
    int initialCount = userConnectionDao.getAllDistinct().size();
    UserConnectionId id = new UserConnectionId(RSPACEUSERNAME, PROVIDER_NAME, DISCRIMINANT);
    UserConnection conn = new UserConnection(id, "accessToken");
    conn.setRefreshToken("refreshToken");
    userConnectionDao.save(conn);
    assertThat(userConnectionDao.getAllDistinct()).hasSize(initialCount + 1);

    UserConnection retrieved = userConnectionDao.get(id);
    assertEquals(conn, retrieved);

    assertThat(userConnectionDao.findByUserNameProviderName("unknownUser", DISCRIMINANT))
        .isNotPresent();
    assertThat(
            userConnectionDao.findByUserNameProviderName(
                RSPACEUSERNAME, PROVIDER_NAME, DISCRIMINANT))
        .isPresent();

    assertEquals(
        1, userConnectionDao.deleteByUserAndProvider(RSPACEUSERNAME, PROVIDER_NAME, DISCRIMINANT));
    assertThat(userConnectionDao.getAllDistinct()).hasSize(initialCount);
  }

  @Test
  public void findListAndMaxRank() {
    final String DISCRIMINANT1 = "discriminant1";
    final String DISCRIMINANT2 = "discriminant2";
    final String DISCRIMINANT3 = "discriminant3";
    int initialCount = userConnectionDao.getAllDistinct().size();
    UserConnectionId id1 = new UserConnectionId(RSPACEUSERNAME, PROVIDER_NAME, DISCRIMINANT1);
    UserConnectionId id2 = new UserConnectionId(RSPACEUSERNAME, PROVIDER_NAME, DISCRIMINANT2);
    UserConnectionId id3 = new UserConnectionId(RSPACEUSERNAME, PROVIDER_NAME, DISCRIMINANT3);
    UserConnection conn1 = new UserConnection(id1, "accessToken1");
    userConnectionDao.save(conn1);

    UserConnection conn2 = new UserConnection(id2, "accessToken2");
    conn2.setRank(
        userConnectionDao.findMaxRankByUserNameProviderName(RSPACEUSERNAME, PROVIDER_NAME).get()
            + 1);
    userConnectionDao.save(conn2);

    UserConnection conn3 = new UserConnection(id3, "accessToken3");
    conn3.setRank(
        userConnectionDao.findMaxRankByUserNameProviderName(RSPACEUSERNAME, PROVIDER_NAME).get()
            + 1);
    userConnectionDao.save(conn3);

    assertThat(userConnectionDao.getAllDistinct()).hasSize(initialCount + 3);

    assertThat(userConnectionDao.findListByUserNameProviderName(RSPACEUSERNAME, PROVIDER_NAME))
        .hasSize(3);
    assertEquals(
        Optional.of(3),
        userConnectionDao.findMaxRankByUserNameProviderName(RSPACEUSERNAME, PROVIDER_NAME));

    List<UserConnection> listConnections =
        userConnectionDao.findListByUserNameProviderName(RSPACEUSERNAME, PROVIDER_NAME);
    for (UserConnection currentUserConnection : listConnections) {
      assertThat(currentUserConnection.getAccessToken()).startsWith("accessToken"); // decrypt works
    }

    assertEquals(3, userConnectionDao.deleteByUserAndProvider(RSPACEUSERNAME, PROVIDER_NAME));
    assertThat(userConnectionDao.getAllDistinct()).hasSize(initialCount);
  }
}
