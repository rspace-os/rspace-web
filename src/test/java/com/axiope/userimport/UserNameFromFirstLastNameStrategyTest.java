package com.axiope.userimport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.dto.UserRegistrationInfo;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class UserNameFromFirstLastNameStrategyTest {

  private UserNameCreationStrategy strat = new UserNameFromFirstLastNameStrategy();

  @Test
  public void testCreateUserNameFromCandidate() {
    UserRegistrationInfo u = new UserRegistrationInfo();
    assertTrue(strat.createUserName("anything", u, new HashSet<String>()));
    assertTrue(u.getUsername().contains("anything"));

    // check can create even if there is a duplicate
    UserRegistrationInfo u2 = new UserRegistrationInfo();
    Set<String> seen = new HashSet<String>();
    seen.add("anything");
    assertTrue(strat.createUserName("anything", u2, seen));
  }

  @Test
  public void testCreateUserNamePadsLEngthToMinLength() {
    UserRegistrationInfo userInfo = new UserRegistrationInfo();
    // too short uname
    assertTrue(strat.createUserName("bob", userInfo, new HashSet<String>()));
    assertTrue(userInfo.getUsername().length() >= User.MIN_UNAME_LENGTH);
  }

  @Test
  public void testCreateUserNameFrom1stLAstName() {
    UserRegistrationInfo userInfo = new UserRegistrationInfo();
    userInfo.setFirstName("Bob");
    userInfo.setLastName("Jones");
    // no candidate uname, but can generate from 1st and last names
    assertTrue(strat.createUserName("", userInfo, new HashSet<String>()));
    assertTrue(userInfo.getUsername().contains("bjones"));

    // but can't work maginc; cannot create uname if no information supplied
    UserRegistrationInfo u2 = new UserRegistrationInfo();
    assertFalse(strat.createUserName("", u2, new HashSet<String>()));
  }
}
