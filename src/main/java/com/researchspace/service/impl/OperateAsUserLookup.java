package com.researchspace.service.impl;

import com.researchspace.model.core.Person;
import com.researchspace.service.UserManager;
import java.util.function.UnaryOperator;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A more complex mechanism that IACtiveUserStrategy, when we want to retrieve more information from
 * the principal than just the username. <br>
 * If <code>currentSubject</code> is a regular logged-in subject, returns the same object.<br>
 * If <code>currentSubject</code> is a sysadmin operating as another user U, will return a Person
 * representation of U queried from the database.
 */
public class OperateAsUserLookup implements UnaryOperator<Person> {

  private @Autowired UserManager userMgr;

  @Override
  public Person apply(Person currentSubject) {
    Person rc = null;
    if (getSubject().isRunAs()) {
      PrincipalCollection prev = getPrincipalCollection();
      String userOperatedAsUsername = prev.getPrimaryPrincipal().toString();
      rc = userMgr.getUserViewByUsername(userOperatedAsUsername);
    } else {
      rc = currentSubject;
    }
    return rc;
  }

  // package - scoped for testing
  PrincipalCollection getPrincipalCollection() {
    return getSubject().getPreviousPrincipals();
  }

  // package - scoped for testing
  Subject getSubject() {
    return SecurityUtils.getSubject();
  }
}
