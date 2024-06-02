package com.axiope.userimport;

import com.researchspace.model.User;
import com.researchspace.model.dto.UserRegistrationInfo;
import java.util.Set;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Attempts to create a a username wither from supplied candidate, or from the first/last name set
 * into the current user, if this is specified.
 *
 * <p>The formatting process is :
 *
 * <ul>
 *   <li>If a candidate usernames is supplied, remove all non-alphanumeric characters
 *   <li>Then, pad random characters to the suffix to make the username at least 6 chars long.
 *   <li>Otherwise, generate a username from 1st/last names in the User object.
 * </ul>
 *
 * To use this class, 1st and last names must be set into the supplied user object to the
 * createUserName() method
 */
public class UserNameFromFirstLastNameStrategy implements UserNameCreationStrategy {

  @Override
  public boolean createUserName(
      String candidate, UserRegistrationInfo current, Set<String> unamess) {

    // maybe username already passed in in csv file
    if (!StringUtils.isBlank(candidate)) {
      String candidateUname = formatString(candidate);
      generateUniqueNameFromCandidate(current, candidateUname, unamess);
      return true;
    }

    // if username blank try to generate from 1stlast name
    if (firstAndLAstNameSet(current)) {
      String candidateUname = current.getFirstName().charAt(0) + current.getLastName();
      candidateUname = formatString(candidateUname).toLowerCase();
      generateUniqueNameFromCandidate(current, candidateUname, unamess);
      return true;
    }

    return false;
  }

  protected String formatString(String uname) {
    uname = uname.replaceAll("[^A-Za-z0-9]", "");
    if (uname.length() < User.MIN_UNAME_LENGTH) {
      String padding =
          RandomStringUtils.random(User.MIN_UNAME_LENGTH - uname.length(), true, true)
              .toLowerCase();
      uname = uname + padding;
    }
    // uname=uname+RandomStringUtils.randomNumeric(3);
    return uname;
  }

  void generateUniqueNameFromCandidate(
      UserRegistrationInfo u, String candidateUname, Set<String> unamess) {
    if (unamess.contains(candidateUname)) {
      candidateUname = candidateUname + RandomStringUtils.randomNumeric(4);
    }
    u.setUsername(candidateUname);
    unamess.add(candidateUname);
  }

  private boolean firstAndLAstNameSet(UserRegistrationInfo u) {
    return !StringUtils.isBlank(u.getFirstName()) && !StringUtils.isBlank(u.getLastName());
  }
}
