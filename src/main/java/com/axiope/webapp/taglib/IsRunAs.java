package com.axiope.webapp.taglib;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import org.apache.shiro.SecurityUtils;

/**
 * Wraps the argument template as a permissions and includes body text if permission is satisfied.
 */
public class IsRunAs extends TagSupport {

  private static final long serialVersionUID = -2545513925674998973L;

  public int doStartTag() throws JspException {
    if (SecurityUtils.getSubject().isRunAs()) {
      return TagSupport.EVAL_BODY_INCLUDE;
    } else {
      return TagSupport.SKIP_BODY;
    }
  }
}
