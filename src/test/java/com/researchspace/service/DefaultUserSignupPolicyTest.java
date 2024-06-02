package com.researchspace.service;

import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.impl.DefaultUserSignupPolicy;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class DefaultUserSignupPolicyTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock UserManager mgr;

  private IMutablePropertyHolder props;
  private DefaultUserSignupPolicy defaultImpl;

  @Before
  public void setUp() {
    defaultImpl = new DefaultUserSignupPolicy();
    props = new PropertyHolder();
    defaultImpl.setProperties(props);
    defaultImpl.setUserManager(mgr);
  }

  @Test(expected = IllegalStateException.class)
  public void testSaveUserThrowsISEIfNotConfiguredForCloud() throws UserExistsException {
    props.setCloud("true");
    defaultImpl.saveUser(TestFactory.createAnyUser("any"), null);
  }
}
