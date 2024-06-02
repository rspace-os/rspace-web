package com.researchspace.testsandbox;

import com.researchspace.model.User;

public class CreationEvent extends GeneralEvent {
  private User user;

  public User getUser() {
    return user;
  }

  public CreationEvent(User user) {
    super();
    this.user = user;
  }
}
