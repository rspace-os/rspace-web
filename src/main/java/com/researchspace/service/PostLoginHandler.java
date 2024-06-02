package com.researchspace.service;

import com.researchspace.model.User;
import javax.servlet.http.HttpSession;

/** Top-level interface to handle post login actions. */
public interface PostLoginHandler {

  /**
   * Handles post-login operations, which could be on 1st login only or on every login Ensures
   * user's account is fully initialised after first login. May return redirect string if the new
   * user should be navigated to non-default page, e.g. to onboarding introduction tour
   *
   * @return url of the page that user should see, or null if default page is fine
   */
  String handlePostLogin(User user, HttpSession session);
}
