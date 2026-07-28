package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.impl.DefaultUserSignupPolicy;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DefaultUserSignupPolicyTest {
  @Mock UserManager mgr;

  private IMutablePropertyHolder props;
  private DefaultUserSignupPolicy defaultImpl;

  @BeforeEach
  public void setUp() {
    defaultImpl = new DefaultUserSignupPolicy();
    props = new PropertyHolder();
    defaultImpl.setProperties(props);
    defaultImpl.setUserManager(mgr);
  }

  @Test
  public void testSaveUserThrowsISEIfNotConfiguredForCloud() throws UserExistsException {
    props.setCloud("true");
    assertThrows(
        IllegalStateException.class,
        () -> defaultImpl.saveUser(TestFactory.createAnyUser("any"), null));
  }
}
