package com.researchspace.api.v1.config;

import static org.junit.Assert.assertTrue;

import com.researchspace.api.v1.controller.ApiAccountInitialiser;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ApiProdConfigTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @InjectMocks ProdAPIConfig cfg;

  @Test
  public void accountInitialisorRequiresApiBetaEnabled() throws Exception {
    User anyUser = TestFactory.createAnyUser("any");
    cfg.setBetaApiEnabled(Boolean.FALSE);
    ApiAccountInitialiser initialiser = cfg.accountInitialiser();

    CoreTestUtils.assertExceptionThrown(
        () -> initialiser.initialiseUser(anyUser), UnsupportedOperationException.class);

    cfg.setBetaApiEnabled(Boolean.TRUE);
    ApiAccountInitialiser realInitialiser = cfg.accountInitialiser();
    assertTrue(realInitialiser instanceof AccountInitialiserImpl);
  }
}
