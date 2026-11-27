package com.researchspace.service.dmp;

import static com.researchspace.service.IntegrationsHandler.ARGOS_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPASSISTANT_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPONLINE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPONLINE_USER_TOKEN;
import static com.researchspace.service.IntegrationsHandler.DMPTOOL_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DSW_APP_NAME;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.service.IntegrationsHandler;

/** Whether a user can currently write to plans held by a given DMP platform. */
public class DmpPlatformStatusResolver {

  /** A platform's state for one user. */
  public enum PlatformStatus {
    OK,
    PLATFORM_OFF,
    NOT_CONNECTED
  }

  private final IntegrationsHandler integrationsHandler;

  public DmpPlatformStatusResolver(IntegrationsHandler integrationsHandler) {
    this.integrationsHandler = integrationsHandler;
  }

  /**
   * @param source a known platform
   * @return the message key of the platform's display name
   */
  public static String platformNameKey(DMPSource source) {
    String platform =
        switch (source) {
          case DMP_TOOL -> "dmpTool";
          case DMP_ONLINE -> "dmpOnline";
          case ARGOS -> "argos";
          case DMP_ASSISTANT -> "dmpAssistant";
          case DSW -> "dsw";
          case UNKNOWN -> throw new IllegalArgumentException("A plan's platform is unknown");
        };
    return "apps.dmp.platformName." + platform;
  }

  /**
   * @param source a known platform
   * @param user the plan's owner
   * @return {@code PLATFORM_OFF} unless the integration is both available and enabled, then {@code
   *     NOT_CONNECTED} if the user has no connection to it, otherwise {@code OK}
   */
  public PlatformStatus resolve(DMPSource source, User user) {
    IntegrationInfo info = integrationsHandler.getIntegration(user, integrationName(source));
    if (info == null || !info.isAvailable() || !info.isEnabled()) {
      return PlatformStatus.PLATFORM_OFF;
    }
    return isConnected(source, info) ? PlatformStatus.OK : PlatformStatus.NOT_CONNECTED;
  }

  private static String integrationName(DMPSource source) {
    return switch (source) {
      case DMP_TOOL -> DMPTOOL_APP_NAME;
      case DMP_ONLINE -> DMPONLINE_APP_NAME;
      case ARGOS -> ARGOS_APP_NAME;
      case DMP_ASSISTANT -> DMPASSISTANT_APP_NAME;
      case DSW -> DSW_APP_NAME;
      case UNKNOWN -> throw new IllegalArgumentException("A plan's platform is unknown");
    };
  }

  private static boolean isConnected(DMPSource source, IntegrationInfo info) {
    return switch (source) {
      case DMP_TOOL, DMP_ASSISTANT -> info.isOauthConnected();
      // a stored token is masked as null; an unset one is ""
      case DMP_ONLINE ->
          info.getOptions().containsKey(DMPONLINE_USER_TOKEN)
              && !"".equals(info.getOptions().get(DMPONLINE_USER_TOKEN));
      case ARGOS, DSW -> true;
      case UNKNOWN -> throw new IllegalArgumentException("A plan's platform is unknown");
    };
  }
}
