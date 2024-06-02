package com.axiope.webapp.taglib;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;

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
