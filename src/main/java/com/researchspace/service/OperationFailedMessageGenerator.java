package com.researchspace.service;

import com.researchspace.model.User;

public interface OperationFailedMessageGenerator {

  String getFailedMessage(String username, String failedAction);

  String getFailedMessage(User user, String failedAction);
}
