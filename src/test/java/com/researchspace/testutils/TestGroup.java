package com.researchspace.testutils;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulation of group and its users for testing. See {@link
 * BaseManagerTestCaseBase}#createTestGroup for usage
 */
public class TestGroup {
  Map<String, User> unameToUser = new HashMap<>();
  public static final String LABADMIN_PREFIX = "labAdmin";
  User pi;

  public User getPi() {
    return pi;
  }

  public TestGroup(User pi) {
    this.pi = pi;
    unameToUser.put(pi.getUsername(), pi);
  }

  Group group;

  public Group getGroup() {
    return group;
  }

  public TestGroup addUser(User u) {
    unameToUser.put(u.getUsername(), u);
    return this;
  }

  public User getUserByPrefix(String prefix) {
    return unameToUser.entrySet().stream()
        .filter(k -> k.getKey().startsWith(prefix))
        .findFirst()
        .get()
        .getValue();
  }

  /**
   * Convenience method to get user1 from group if present
   *
   * @return
   */
  public User u1() {
    return getUserByPrefix("u1");
  }

  /**
   * Convenience method to get user2 from group if set
   *
   * @return
   */
  public User u2() {
    return getUserByPrefix("u2");
  }

  /**
   * Convenience method to get user3 from group if set
   *
   * @return
   */
  public User u3() {
    return getUserByPrefix("u3");
  }

  /**
   * Access to underlying map for cases where it's necessary to modify the group after creation.
   * This breaks encapsulation but is useful sometimes.
   *
   * @return
   */
  public Map<String, User> getUnameToUser() {
    return unameToUser;
  }

  public void setGroup(Group group) {
    this.group = group;
  }
}
