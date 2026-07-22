package com.researchspace.service.impl;

import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.extmessages.base.MessageDetails;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ExternalMessageHandler;
import com.researchspace.service.ExternalMessageSenderFactory;
import com.researchspace.service.MessageOrRequestCreatorManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserAppConfigManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class ExternalMessageHandlerImpl implements ExternalMessageHandler {

  static final String SEND_FAILED_STATUS_MSG_KEY = "external.messaging.send.failedStatus";
  static final String MSTEAMS_UNAUTHORIZED_MSG_KEY = "external.messaging.send.msTeamsUnauthorized";

  Logger log = LoggerFactory.getLogger(ExternalMessageHandlerImpl.class);

  private @Autowired UserAppConfigManager userAppMgr;
  private @Autowired ExternalMessageSenderFactory messageSenderFactory;
  private @Autowired IPermissionUtils permUtils;
  private @Autowired MessageSourceUtils messageSource;

  @Autowired MessageOrRequestCreatorManager commMgr;

  @Override
  public ServiceOperationResult<ResponseEntity<String>> sendExternalMessage(
      String message, Long appConfigElementSetId, List<Long> recordIds, User user) {
    Optional<AppConfigElementSet> optCfg =
        userAppMgr.findByAppConfigElementSetId(appConfigElementSetId);
    if (optCfg.isPresent()) {
      AppConfigElementSet cfg = optCfg.get();
      permUtils.assertIsPermitted(
          cfg.getUserAppConfig(), PermissionType.READ, user, " use AppConfig  ");
      App app = cfg.getUserAppConfig().getApp();
      Optional<ExternalMessageSender> extMessageSender =
          messageSenderFactory.findMessageSenderForApp(app);
      if (extMessageSender.isPresent()) {
        ExternalMessageSender sender = extMessageSender.get();
        return sendMessage(message, optCfg.get(), sender, recordIds, user);
      } else {
        log.warn("No message sender registered for app : {}, aborting message ", app.getName());
        return new ServiceOperationResult<ResponseEntity<String>>(null, false);
      }
    } else {
      log.error(
          "Couldn't find UserConfiguration for user {} and elementSetId {}, aborting message",
          user.getUsername(),
          appConfigElementSetId);
      return new ServiceOperationResult<ResponseEntity<String>>(null, false);
    }
  }

  private ServiceOperationResult<ResponseEntity<String>> sendMessage(
      String message,
      AppConfigElementSet appConfigElementSet,
      ExternalMessageSender extMessageSender,
      List<Long> recordIds,
      User user) {
    List<MessageOrRequest> messages = new ArrayList<>();
    for (Long id : recordIds) {
      MsgOrReqstCreationCfg cfg = new MsgOrReqstCreationCfg();
      cfg.setMessageType(MessageType.SIMPLE_MESSAGE);
      cfg.setRecordId(id);
      cfg.setOptionalMessage(message);
      MessageOrRequest msg = commMgr.createRequest(cfg, user.getUsername(), Collections.emptySet());
      messages.add(msg);
    }
    MessageDetails details =
        new MessageDetails(
            user,
            message,
            messages.stream().map(msg -> msg.getRecord()).collect(Collectors.toList()));
    ResponseEntity<String> resp;
    try {
      resp = extMessageSender.sendMessage(details, appConfigElementSet, user);
    } catch (RestClientResponseException e) {
      String appName = appConfigElementSet.getUserAppConfig().getApp().getName();
      log.warn(
          "Posting external message for app {} failed with status {} {}",
          appName,
          e.getRawStatusCode(),
          e.getStatusText());
      return new ServiceOperationResult<ResponseEntity<String>>(
          null, false, webhookErrorMessage(appName, e));
    } catch (RestClientException e) {
      log.warn("Posting external message failed: {}", e.getMessage());
      return new ServiceOperationResult<ResponseEntity<String>>(
          null, false, messageSource.getMessage(SEND_FAILED_MSG_KEY));
    }
    return new ServiceOperationResult<ResponseEntity<String>>(
        resp, resp.getStatusCode().is2xxSuccessful());
  }

  /**
   * A 401 from a Microsoft Teams Workflows webhook almost always means the workflow was created
   * from a template restricted to authenticated callers ('from specific people' / 'from people in
   * an org'), which RSpace's anonymous webhook posts can never satisfy, so we give specific
   * guidance for that case.
   */
  private String webhookErrorMessage(String appName, RestClientResponseException e) {
    if (e.getRawStatusCode() == HttpStatus.UNAUTHORIZED.value()
        && App.APP_MSTEAMS.equals(appName)) {
      return messageSource.getMessage(MSTEAMS_UNAUTHORIZED_MSG_KEY);
    }
    return messageSource.getMessage(
        SEND_FAILED_STATUS_MSG_KEY, new Object[] {e.getRawStatusCode() + " " + e.getStatusText()});
  }
}
