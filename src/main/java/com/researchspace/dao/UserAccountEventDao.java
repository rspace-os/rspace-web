package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.events.UserAccountEvent;
import java.util.List;

public interface UserAccountEventDao extends GenericDao<UserAccountEvent, String> {

  /** Gets UserAccountEvents for user */
  public List<UserAccountEvent> getAccountEventsForUser(User toRetrieve);
}
