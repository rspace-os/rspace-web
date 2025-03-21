package com.researchspace.webapp.integrations.orcid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class OrcidControllerTest extends SpringTransactionalTest {

  private @Autowired OrcidController controller;
  private @Autowired IntegrationsHandler integrationsHandler;
  private @Autowired UserExternalIdResolver extIdResolver;
  private @Autowired SystemPropertyManager systemPropertyManager;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    User sysadmin = logoutAndLoginAsSysAdmin();
    systemPropertyManager.save(
        SystemPropertyName.ORCID_AVAILABLE, HierarchicalPermission.ALLOWED, sysadmin);
    RSpaceTestUtils.logout();
  }

  @Test
  public void updatingUserOrcidId() {

    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("orcidUser"));
    logoutAndLoginAs(user);
    assertFalse(extIdResolver.getExternalIdForUser(user, IdentifierScheme.ORCID).isPresent());
    assertTrue(
        extIdResolver.isIdentifierSchemeAvailable(
            user, IdentifierScheme.ORCID)); // enabled by default

    String testOrcidId1 = "http://orcid.id/testId";
    controller.updateUserOrcidApp(user, testOrcidId1);
    assertTrue(extIdResolver.getExternalIdForUser(user, IdentifierScheme.ORCID).isPresent());

    // orcid id should be set now
    IntegrationInfo orcidIntegration =
        integrationsHandler.getIntegration(user, IntegrationsHandler.ORCID_APP_NAME);
    Map<String, Object> optionSets = orcidIntegration.getOptions();
    assertEquals(1, optionSets.size());
    Map<String, String> options = (Map<String, String>) optionSets.values().iterator().next();
    assertEquals(1, options.size());
    assertEquals(testOrcidId1, options.values().iterator().next());

    // call update method again, for different code
    String testOrcidId2 = "http://orcid.id/testId2";
    controller.updateUserOrcidApp(user, testOrcidId2);

    // there should be still one options set, with a single, updated value
    IntegrationInfo updatedIntegration =
        integrationsHandler.getIntegration(user, IntegrationsHandler.ORCID_APP_NAME);
    Map<String, Object> updatedSet = updatedIntegration.getOptions();
    assertEquals(1, updatedSet.size());
    Map<String, String> updatedOptions =
        (Map<String, String>) optionSets.values().iterator().next();
    assertEquals(1, updatedOptions.size());
    assertEquals(testOrcidId1, updatedOptions.values().iterator().next());
  }
}
