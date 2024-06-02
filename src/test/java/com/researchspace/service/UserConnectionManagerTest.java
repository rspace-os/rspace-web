package com.researchspace.service;

import static com.researchspace.model.record.TestFactory.createUserConnection;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import com.researchspace.dao.UserConnectionDao;
import com.researchspace.service.impl.UserConnectionManagerImpl;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UserConnectionManagerTest {
  public @Rule MockitoRule mockito = MockitoJUnit.rule();

  @Mock UserConnectionDao connectionDao;
  @InjectMocks UserConnectionManagerImpl userConnMgr;

  @Test
  public void testFindByUserNameProviderName() {
    String rspaceUserName = "username";
    when(connectionDao.findByUserNameProviderName(rspaceUserName, "providerName"))
        .thenReturn(Optional.ofNullable(createUserConnection(rspaceUserName)));

    assertNotNull(userConnMgr.findByUserNameProviderName(rspaceUserName, "providerName"));
  }
}
