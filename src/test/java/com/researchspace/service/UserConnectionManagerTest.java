package com.researchspace.service;

import static com.researchspace.model.record.TestFactory.createUserConnection;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.UserConnectionDao;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.impl.UserConnectionManagerImpl;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UserConnectionManagerTest {

  public static final String PROVIDER_NAME = "provider";
  public static final String USERNAME = "username";
  public static final String PROVIDER_USER_ID = "providerUserId";
  public @Rule MockitoRule mockito = MockitoJUnit.rule();

  @Mock UserConnectionDao connectionDao;
  @InjectMocks UserConnectionManagerImpl userConnMgr;

  @Test
  public void testFindByUserNameProviderName() {
    Optional<UserConnection> result = Optional.ofNullable(createUserConnection(USERNAME));
    when(connectionDao.findByUserNameProviderName(USERNAME, PROVIDER_NAME)).thenReturn(result);

    assertEquals(result, userConnMgr.findByUserNameProviderName(USERNAME, PROVIDER_NAME));
    verify(connectionDao).findByUserNameProviderName(USERNAME, PROVIDER_NAME);
  }

  @Test
  public void testFindMaxRankByUserNameProviderName() {
    Optional<Integer> result = Optional.of(2);
    when(connectionDao.findMaxRankByUserNameProviderName(USERNAME, PROVIDER_NAME))
        .thenReturn(result);

    assertEquals(result, userConnMgr.findMaxRankByUserNameProviderName(USERNAME, PROVIDER_NAME));
    verify(connectionDao).findMaxRankByUserNameProviderName(USERNAME, PROVIDER_NAME);
  }

  @Test
  public void testFindListByUserNameProviderName() {
    List<UserConnection> result = List.of(createUserConnection(USERNAME));
    when(connectionDao.findListByUserNameProviderName(USERNAME, PROVIDER_NAME)).thenReturn(result);

    assertEquals(result, userConnMgr.findListByUserNameProviderName(USERNAME, PROVIDER_NAME));
    verify(connectionDao).findListByUserNameProviderName(USERNAME, PROVIDER_NAME);
  }

  @Test
  public void testFindByUserNameProviderNameAndDiscriminant() {
    Optional<UserConnection> result = Optional.ofNullable(createUserConnection(USERNAME));
    when(connectionDao.findByUserNameProviderName(USERNAME, PROVIDER_NAME, PROVIDER_USER_ID))
        .thenReturn(result);

    assertEquals(
        result, userConnMgr.findByUserNameProviderName(USERNAME, PROVIDER_NAME, PROVIDER_USER_ID));
    verify(connectionDao).findByUserNameProviderName(USERNAME, PROVIDER_NAME, PROVIDER_USER_ID);
  }

  @Test
  public void testDeleteByUserNameProviderNameAndDiscriminant() {
    int result = 1;
    when(connectionDao.deleteByUserAndProvider(USERNAME, PROVIDER_NAME, PROVIDER_USER_ID))
        .thenReturn(result);

    assertEquals(
        result, userConnMgr.deleteByUserAndProvider(USERNAME, PROVIDER_NAME, PROVIDER_USER_ID));
    verify(connectionDao).deleteByUserAndProvider(USERNAME, PROVIDER_NAME, PROVIDER_USER_ID);
  }

  @Test
  public void testDeleteByUserNameProviderName() {
    int result = 0;
    when(connectionDao.deleteByUserAndProvider(USERNAME, PROVIDER_NAME)).thenReturn(result);

    assertEquals(result, userConnMgr.deleteByUserAndProvider(PROVIDER_NAME, USERNAME));
    verify(connectionDao).deleteByUserAndProvider(USERNAME, PROVIDER_NAME);
  }
}
