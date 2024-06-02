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
import com.researchspace.service.UserAppConfigManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

public class ExternalMessageHandlerImpl implements ExternalMessageHandler {
  Logger log = LoggerFactory.getLogger(ExternalMessageHandlerImpl.class);

  private @Autowired UserAppConfigManager userAppMgr;
  private @Autowired ExternalMessageSenderFactory messageSenderFactory;
  private @Autowired IPermissionUtils permUtils;

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
    ResponseEntity<String> resp = extMessageSender.sendMessage(details, appConfigElementSet, user);
    return new ServiceOperationResult<ResponseEntity<String>>(
        resp, resp.getStatusCode().is2xxSuccessful());
  }
}
