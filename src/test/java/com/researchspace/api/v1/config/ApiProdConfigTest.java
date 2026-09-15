package com.researchspace.api.v1.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.controller.ApiAccountInitialiser;
import com.researchspace.model.User;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ApiProdConfigTest {

  @InjectMocks ProdAPIConfig cfg;

  @Test
  public void accountInitialisorRequiresApiBetaEnabled() throws Exception {
    User anyUser = TestFactory.createAnyUser("any");
    cfg.setBetaApiEnabled(Boolean.FALSE);
    ApiAccountInitialiser initialiser = cfg.accountInitialiser();

    assertThrows(UnsupportedOperationException.class, () -> initialiser.initialiseUser(anyUser));

    cfg.setBetaApiEnabled(Boolean.TRUE);
    ApiAccountInitialiser realInitialiser = cfg.accountInitialiser();
    assertTrue(realInitialiser instanceof AccountInitialiserImpl);
  }
}
