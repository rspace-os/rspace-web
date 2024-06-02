package com.axiope.webapp.taglib;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import java.io.IOException;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserRoleInGroup extends TagSupport {

  private static final Logger LOG = LoggerFactory.getLogger(UserRoleInGroup.class.getName());
  private static final long serialVersionUID = 1L;
  private User user;
  private Group group;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Group getGroup() {
    return group;
  }

  public void setGroup(Group group) {
    this.group = group;
  }

  public int doStartTag() throws JspException {
    try {
      // Get the writer object for output.
      JspWriter out = pageContext.getOut();

      // Perform substr operation on string.
      out.println(getString());

    } catch (IOException e) {
      LOG.error("Error writing start tag: ", e);
    }

    return SKIP_BODY;
  }

  String getString() {
    return group.getRoleForUser(user).getLabel();
  }
}
