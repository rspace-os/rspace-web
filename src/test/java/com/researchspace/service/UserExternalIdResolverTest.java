package com.researchspace.service;

import static com.researchspace.model.record.TestFactory.createAnyUser;
import static com.researchspace.repository.spi.IdentifierScheme.ORCID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.impl.UserExternalIdResolverImpl;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UserExternalIdResolverTest {
  public @Rule MockitoRule rule = MockitoJUnit.rule();
  UserExternalIdResolverImpl idResolver = null;
  @Mock IntegrationsHandler handler;
  User anyUser;

  @Before
  public void setUp() throws Exception {
    idResolver = new UserExternalIdResolverImpl();
    idResolver.setAppHandler(handler);
    anyUser = createAnyUser("any");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetExternalIdForUserHandlesUnavailable() {
    // orcid must be available
    setUpOrchidIsNotAvailable();
    assertEquals(Optional.empty(), idResolver.getExternalIdForUser(anyUser, ORCID));
    // app must be present as well
    setUpOrcidAvailable();
    when(handler.getIntegration(anyUser, ORCID.name())).thenReturn(null);
    assertEquals(Optional.empty(), idResolver.getExternalIdForUser(anyUser, ORCID));
  }

  @Test
  public void testGetExternalIdForUserAppPresent() {
    setUpOrcidAvailable();
    IntegrationInfo info = createAvailableOrcid();
    when(handler.getIntegration(anyUser, ORCID.name())).thenReturn(info);
    assertTrue(idResolver.getExternalIdForUser(anyUser, ORCID).isPresent());
  }

  private IntegrationInfo createAvailableOrcid() {
    IntegrationInfo info = setupIntegrationInfo();
    info.setAvailable(true);
    return info;
  }

  private IntegrationInfo createUnAvailableOrcid() {
    IntegrationInfo info = setupIntegrationInfo();
    info.setAvailable(false);
    return info;
  }

  private IntegrationInfo setupIntegrationInfo() {
    IntegrationInfo info = new IntegrationInfo();
    info.setName("orcid.app");

    Map<String, Object> map = new HashMap<>();
    Map<String, String> internal = new HashMap<>();
    internal.put("key", "value");
    map.put("any", internal);
    info.setOptions(map);
    return info;
  }

  private void setUpOrchidIsNotAvailable() {
    IntegrationInfo info = createUnAvailableOrcid();
    when(handler.getIntegration(anyUser, ORCID.name())).thenReturn(info);
  }

  private void setUpOrcidAvailable() {
    IntegrationInfo info = createAvailableOrcid();
    when(handler.getIntegration(anyUser, ORCID.name())).thenReturn(info);
  }

  @Test
  public void testIsIdentifierSchemeAvailable() {
    assertFalse(idResolver.isIdentifierSchemeAvailable(anyUser, IdentifierScheme.ISNI));
    setUpOrcidAvailable();
    assertTrue(isOrcidAvailable());
    setUpOrchidIsNotAvailable();
    assertFalse(isOrcidAvailable());
  }

  private boolean isOrcidAvailable() {
    return idResolver.isIdentifierSchemeAvailable(anyUser, ORCID);
  }
}
