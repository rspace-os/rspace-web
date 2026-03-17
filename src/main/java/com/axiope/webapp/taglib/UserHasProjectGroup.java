package com.axiope.webapp.taglib;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;

public class UserHasProjectGroup extends TagSupport {
  private User user;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public int doStartTag() throws JspException {
    for (Group g : user.getGroups()) {
      if (g.isProjectGroup()) {
        return TagSupport.EVAL_BODY_INCLUDE;
      }
    }
    return TagSupport.SKIP_BODY;
  }
}
