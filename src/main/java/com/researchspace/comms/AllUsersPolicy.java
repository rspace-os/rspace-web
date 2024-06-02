package com.researchspace.comms;

import static com.researchspace.comms.CommunicationTargetFinderPolicy.usersWithAnonymousRemoved;

import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.Record;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Simple policy that returns a set of all users on the system, and does not consider permissions.
 * <br>
 * Assumed that this class will be used in a transaction (i.e., from a service method)
 */
@Component("allUserPolicy")
public class AllUsersPolicy implements CommunicationTargetFinderPolicy {

  @Autowired private UserDao userDao;

  @Override
  public Set<User> findPotentialTargetsFor(
      MessageType type, Record r, String searchTerm, User sender) {
    List<User> targets = null;
    if (searchTerm != null) {
      targets = userDao.searchUsers(searchTerm);
    } else {
      targets = userDao.getUsers();
    }
    Set<User> uniqueUsers = new TreeSet<User>(targets);
    return usersWithAnonymousRemoved(uniqueUsers);
  }

  @Override
  public String getFailureMessageIfUserInvalidTarget() {
    return ""; // no user is an invalid target, should never be called.
  }
}
