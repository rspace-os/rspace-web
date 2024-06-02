package com.researchspace.service.impl;

import static com.researchspace.service.impl.UserConnectionManagerImpl.SAVE_CONNECTION_SPEL;
import static com.researchspace.testutils.SpringELTestUtils.evaluateSpel;
import static org.junit.Assert.assertEquals;

import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.record.TestFactory;
import org.junit.Test;

public class UserConnectionImplTest {

  @Test
  public void cacheEvictionSpel() {
    UserConnection connection = TestFactory.createUserConnection("user1");
    String expectedCacheKey = connection.getId().getUserId() + connection.getId().getProviderId();
    String evaluatedSpel =
        evaluateSpel(connection, SAVE_CONNECTION_SPEL.replaceAll("#connection.", ""));

    assertEquals(expectedCacheKey, evaluatedSpel);
  }
}
