package com.researchspace.comms;

import static com.researchspace.comms.CommunicationTargetFinderPolicy.usersWithAnonymousRemoved;

import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.Record;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Policy to get all PIs on the system */
@Component("findAllPisPolicy")
public class AllPIsFinderPolicy implements CommunicationTargetFinderPolicy {

  @Autowired private UserDao userDao;

  @Override
  public Set<User> findPotentialTargetsFor(
      MessageType type, Record r, String searchTerm, User sender) {
    Set<User> usersRC = userDao.getAllGroupPis(searchTerm);

    // don't want to send to self.
    usersRC.remove(sender);

    return usersWithAnonymousRemoved(usersRC);
  }

  @Override
  public String getFailureMessageIfUserInvalidTarget() {
    return " Only PIs may be targets of this request";
  }
}
