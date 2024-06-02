package com.axiope.userimport;

import com.researchspace.model.dto.UserRegistrationInfo;
import com.researchspace.model.dtos.UserValidator;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Validates the username using the standard UserValidator, and rejects a username if it is invalid
 * without trying to change it.
 */
public class RequireValidUserNameStrategy implements UserNameCreationStrategy {

  @Autowired private UserValidator validator;

  @Override
  public boolean createUserName(
      String candidate, UserRegistrationInfo current, Set<String> seenUsernames) {
    if (!validator.validateUsername(candidate).equals(UserValidator.FIELD_OK)) {
      return false;
    }
    current.setUsername(candidate);
    seenUsernames.add(candidate);
    return true;
  }

  /*
   * ================
   * for tests
   * ================
   */

  protected void setValidator(UserValidator validator) {
    this.validator = validator;
  }
}
