package com.researchspace.service.impl;

import static com.researchspace.session.SessionAttributeUtils.FIRST_LOGIN;
import static com.researchspace.session.SessionAttributeUtils.FIRST_LOGIN_HANDLED;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.researchspace.model.User;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.InitializedContent;
import com.researchspace.service.PostAnyLoginAction;
import com.researchspace.service.PostFirstLoginAction;
import com.researchspace.service.PostLoginHandler;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class PostLoginHandlerImpl implements PostLoginHandler {

  private @Autowired IContentInitializer contentInitializer;
  @Autowired private UserContentUpdater userContentUpdater;

  private List<PostFirstLoginAction> postFirstLoginActions = new ArrayList<>();
  private List<PostAnyLoginAction> postAnyLoginActions = new ArrayList<>();

  public void setPostFirstLoginActions(List<PostFirstLoginAction> postFirstLoginActions) {
    this.postFirstLoginActions = postFirstLoginActions;
  }

  public void setPostAnyLoginActions(List<PostAnyLoginAction> postAnyLoginActions) {
    this.postAnyLoginActions = postAnyLoginActions;
  }

  @Override
  public String handlePostLogin(User user, HttpSession session) {
    String rc;
    if (isUserFirstLogin(user, session)) {
      rc = handleFirstLogin(user, session);
    } else {
      rc = handleEveryLogin(user, session);
    }

    /* PRT-674: in some cases handleFirstLogin method won't run folder initialization (e.g. egnyte),
     * so check isContentInitialized before trying to run the updates */
    if (user.isContentInitialized()) {
      userContentUpdater.doUserContentUpdates(user);
    }
    return rc;
  }

  String handleEveryLogin(User user, HttpSession session) {
    String redirectRC = null;
    for (PostAnyLoginAction action : postAnyLoginActions) {
      String redirect = action.onAnyLogin(user, session);
      if (!isEmpty(redirect)) {
        redirectRC = redirect;
      }
    }
    return redirectRC;
  }

  // all redirects are returned. 1st login marked handled when all have been invoked
  private String handleFirstLogin(User user, HttpSession session) {
    log.info(
        "Handling first login for user '{}' for session {}",
        user.getUsername(),
        abbreviate(session.getId(), 10));
    String redirect = preContentInitialize(user, session);
    if (!isBlank(redirect)) {
      return redirect;
    }
    InitializedContent initializedContent = initializeUserContent(user);
    redirect = postContentInitialize(user, session, initializedContent);
    if (!isBlank(redirect)) {
      return redirect;
    }
    // this globally marks all first login handlers as having run once
    session.setAttribute(FIRST_LOGIN_HANDLED, true);
    return null;
  }

  // steps to be performed after content is initialised
  private String postContentInitialize(
      User user, HttpSession session, InitializedContent initializedContent) {
    for (PostFirstLoginAction action : postFirstLoginActions) {
      String redirect =
          action.onFirstLoginAfterContentInitialisation(user, session, initializedContent);
      if (!isEmpty(redirect)) {
        return redirect;
      }
    }
    return null;
  }

  private InitializedContent initializeUserContent(User user) {
    if (user.isContentInitialized()) {
      return null;
    }
    InitializedContent initializedContent = contentInitializer.init(user.getId());
    if (initializedContent != null) {
      user.setContentInitialized(initializedContent.getUser().isContentInitialized());
    }
    return initializedContent;
  }

  private String preContentInitialize(User user, HttpSession session) {

    for (PostFirstLoginAction action : postFirstLoginActions) {
      String redirect = action.onFirstLoginBeforeContentInitialisation(user, session);
      if (!StringUtils.isEmpty(redirect)) {
        return redirect;
      }
    }
    return null;
  }

  private boolean isUserFirstLogin(User user, HttpSession session) {
    return (isFirstLogin(session) && firstLoginNotHandled(session)) || !user.isContentInitialized();
  }

  private boolean firstLoginNotHandled(HttpSession session) {
    return !Boolean.TRUE.equals(session.getAttribute(FIRST_LOGIN_HANDLED));
  }

  private boolean isFirstLogin(HttpSession session) {
    return Boolean.TRUE.equals(session.getAttribute(FIRST_LOGIN));
  }
}
