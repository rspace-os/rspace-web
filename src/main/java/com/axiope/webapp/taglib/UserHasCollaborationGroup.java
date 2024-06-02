package com.axiope.webapp.taglib;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;

public class UserHasCollaborationGroup extends TagSupport {
  /** */
  private static final long serialVersionUID = 1695835920230544021L;

  private User user;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public int doStartTag() throws JspException {
    for (Group g : user.getGroups()) {
      if (g.isCollaborationGroup()) {
        return TagSupport.EVAL_BODY_INCLUDE;
      }
    }
    return TagSupport.SKIP_BODY;
  }
}
