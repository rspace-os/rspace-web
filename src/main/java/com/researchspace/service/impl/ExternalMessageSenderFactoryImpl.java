package com.researchspace.service.impl;

import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.model.apps.App;
import com.researchspace.service.ExternalMessageSenderFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExternalMessageSenderFactoryImpl implements ExternalMessageSenderFactory {

  List<ExternalMessageSender> messageSenders = new ArrayList<>();

  @Override
  public Optional<ExternalMessageSender> findMessageSenderForApp(App app) {
    return messageSenders.stream().filter((sender) -> sender.supportsApp(app)).findFirst();
  }

  public void setMessageSenders(List<ExternalMessageSender> messageSenders) {
    this.messageSenders = messageSenders;
  }
}
