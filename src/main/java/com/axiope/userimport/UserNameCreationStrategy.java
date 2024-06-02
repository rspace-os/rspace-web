package com.axiope.userimport;

import com.researchspace.model.dto.UserRegistrationInfo;
import java.util.Set;

/**
 * Strategy inteface to define a mechanism to create new usernames programmatically.</br> All
 * usernames should be unique in the system.
 */
public interface UserNameCreationStrategy {

  /**
   * @param candidate A candidate username, can be empty
   * @param current A User object that holds some information about the user, from which a user name
   *     can be constructed.
   * @param seenUsernames A {@link Set} of usernames already seen, for determining uniqueness
   * @return <code>true</code> if this method could set a username into the <code>current</code>
   *     user, <code>false</code> otherwise.
   */
  boolean createUserName(String candidate, UserRegistrationInfo current, Set<String> seenUsernames);
}
