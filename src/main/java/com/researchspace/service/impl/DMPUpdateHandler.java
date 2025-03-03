package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.service.DMPManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.webapp.integrations.dmptool.DMPToolDMPProvider;
import java.net.URL;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class DMPUpdateHandler {

  private @Autowired DMPManager dmpManager;
  private @Autowired DMPToolDMPProvider dmpClient;
  private @Autowired IntegrationsHandler integrationsHandler;

  public DMPUpdateHandler() {}

  /**
   * @param doiSupplier A Supplier of a URL (might
   * @param subject
   * @param dmpUserIds
   */
  void updateDMPS(Supplier<URL> doiSupplier, User subject, List<Long> dmpUserIds) {
    if (dmpUserIds.isEmpty()) {
      return;
    }
    URL doi = doiSupplier.get();
    if (doi == null) {
      log.warn("No URL Id supplied - couldn't update DMPs: {}", StringUtils.join(dmpUserIds, ","));
      return;
    }
    IntegrationInfo inInfo =
        integrationsHandler.getIntegration(subject, IntegrationsHandler.DMPTOOL_APP_NAME);
    if (inInfo.isOAuthAppUsable()) {
      log.info("DMPTool integration is usable");
      List<DMPUser> dmpsForUser = dmpManager.findDMPsForUser(subject);
      if (dmpsForUser.isEmpty()) {
        log.info("No DMPs found for {}", subject.getUsername());
        return;
      }
      List<String> dmpsToUpdate =
          dmpsForUser.stream()
              .filter(dmpUser -> dmpUserIds.contains(dmpUser.getId()))
              .map(DMPUser::getDmpId)
              .collect(Collectors.toList());

      for (String dmpStr : dmpsToUpdate) {
        var dmpUpdated = dmpClient.addDoiIdentifierToDMP(dmpStr, doi.toString(), subject);
        if (!dmpUpdated.isSucceeded()) {
          log.error("Updating didn't succeed : {}", dmpUpdated.getMessage());
        } else {
          log.info("Updated DMP {}", dmpStr);
        }
      }

    } else {
      log.info("DMPTool integration is not enabled for {}", subject.getUsername());
    }
  }
}
