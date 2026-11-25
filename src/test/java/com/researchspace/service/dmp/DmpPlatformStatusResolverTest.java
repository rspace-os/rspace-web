package com.researchspace.service.dmp;

import static com.researchspace.service.IntegrationsHandler.ARGOS_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPONLINE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPONLINE_USER_TOKEN;
import static com.researchspace.service.IntegrationsHandler.DMPTOOL_APP_NAME;
import static com.researchspace.service.dmp.DmpPlatformStatusResolver.PlatformStatus.NOT_CONNECTED;
import static com.researchspace.service.dmp.DmpPlatformStatusResolver.PlatformStatus.OK;
import static com.researchspace.service.dmp.DmpPlatformStatusResolver.PlatformStatus.PLATFORM_OFF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.TestFactory;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DmpPlatformStatusResolverTest {

  @Mock IntegrationsHandler integrationsHandler;
  DmpPlatformStatusResolver resolver;
  User user;

  @BeforeEach
  void setUp() {
    resolver = new DmpPlatformStatusResolver(integrationsHandler);
    user = TestFactory.createAnyUser("any");
  }

  @ParameterizedTest
  @CsvSource({
    "DMP_TOOL, DMPTool",
    "DMP_ONLINE, DMPonline",
    "ARGOS, Argos",
    "DSW, DSW / FAIR Wizard",
    "DMP_ASSISTANT, DMP Assistant"
  })
  void platformNameKeyResolvesToCatalogName(DMPSource source, String name) {
    MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
    assertEquals(name, messages.getMessage(DmpPlatformStatusResolver.platformNameKey(source)));
  }

  @ParameterizedTest
  @CsvSource({
    "DMP_TOOL, DMPTOOL",
    "DMP_ASSISTANT, DMPASSISTANT",
    "DMP_ONLINE, DMPONLINE",
    "ARGOS, ARGOS"
  })
  void platformOffWhenNotAvailableOrNotEnabled(DMPSource source, String integration) {
    stub(integration, info(false, true, true));
    assertEquals(PLATFORM_OFF, resolver.resolve(source, user));

    stub(integration, info(true, false, true));
    assertEquals(PLATFORM_OFF, resolver.resolve(source, user));
  }

  @Test
  void platformOffWhenNoIntegrationInfo() {
    stub(DMPTOOL_APP_NAME, null);
    assertEquals(PLATFORM_OFF, resolver.resolve(DMPSource.DMP_TOOL, user));
  }

  @ParameterizedTest
  @CsvSource({"DMP_TOOL, DMPTOOL", "DMP_ASSISTANT, DMPASSISTANT"})
  void oauthPlatformsUseOauthConnection(DMPSource source, String integration) {
    stub(integration, info(true, true, true));
    assertEquals(OK, resolver.resolve(source, user));

    stub(integration, info(true, true, false));
    assertEquals(NOT_CONNECTED, resolver.resolve(source, user));
  }

  @Test
  void dmpOnlineUsesStoredUserToken() {
    IntegrationInfo noToken = info(true, true, false);
    stub(DMPONLINE_APP_NAME, noToken);
    assertEquals(NOT_CONNECTED, resolver.resolve(DMPSource.DMP_ONLINE, user));

    IntegrationInfo unsetToken = info(true, true, false);
    unsetToken.getOptions().put(DMPONLINE_USER_TOKEN, "");
    stub(DMPONLINE_APP_NAME, unsetToken);
    assertEquals(NOT_CONNECTED, resolver.resolve(DMPSource.DMP_ONLINE, user));

    // a stored secret is masked as null
    IntegrationInfo storedToken = info(true, true, false);
    storedToken.getOptions().put(DMPONLINE_USER_TOKEN, null);
    stub(DMPONLINE_APP_NAME, storedToken);
    assertEquals(OK, resolver.resolve(DMPSource.DMP_ONLINE, user));
  }

  @Test
  void argosIsNeverNotConnected() {
    stub(ARGOS_APP_NAME, info(true, true, false));
    assertEquals(OK, resolver.resolve(DMPSource.ARGOS, user));
  }

  private void stub(String integration, IntegrationInfo info) {
    when(integrationsHandler.getIntegration(user, integration)).thenReturn(info);
  }

  private IntegrationInfo info(boolean available, boolean enabled, boolean oauthConnected) {
    IntegrationInfo info = new IntegrationInfo();
    info.setAvailable(available);
    info.setEnabled(enabled);
    info.setOauthConnected(oauthConnected);
    info.setOptions(new HashMap<>());
    return info;
  }
}
