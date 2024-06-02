package com.researchspace.service;

import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.model.apps.App;
import java.util.Optional;

public interface ExternalMessageSenderFactory {

  Optional<ExternalMessageSender> findMessageSenderForApp(App app);
}
