package com.axiope.userimport;

import com.researchspace.model.User;
import javax.servlet.http.HttpServletRequest;

/**
 * Interface to customize post-registration behaviour after manual or automated user registration.
 */
public interface IPostUserCreationSetUp {

  /**
   * Performs post-user persistent actions such as sending email, creating a group, etc
   *
   * @param created The newly created user
   * @param request The request creating the user
   * @param origPwd The original password of the user (after saving, it is hashed and
   *     irretrievable).
   */
  void postUserCreate(User created, HttpServletRequest request, String origPwd);
}
