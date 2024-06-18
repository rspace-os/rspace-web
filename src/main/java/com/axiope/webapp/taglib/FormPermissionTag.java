package com.axiope.webapp.taglib;

import com.researchspace.model.permissions.FormPermissionAdapter;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.RSForm;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import org.apache.shiro.SecurityUtils;

/** Wraps the argument form as a permissions and includes body text if permission is satisfied. */
public class FormPermissionTag extends TagSupport {

  private static final long serialVersionUID = -2545513925674998973L;
  private RSForm form;
  private String action;

  /**
   * Compulsory attribute; should be a String representation of a Permission type
   *
   * @return
   */
  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  /**
   * Compulsory attribute
   *
   * @return A {@link RSForm}
   */
  public RSForm getForm() {
    return form;
  }

  public void setForm(RSForm form) {
    this.form = form;
  }

  /**
   * @throws JspException if template or action is null
   */
  public int doStartTag() throws JspException {
    if (action == null || form == null) {
      throw new IllegalStateException(" template or action is null");
    }
    RSForm t = getForm();
    FormPermissionAdapter tpa = new FormPermissionAdapter(t);
    PermissionType pt = PermissionType.valueOf(action);
    tpa.setAction(pt);

    boolean show = checkAuthorisation(tpa);
    if (show) {
      return TagSupport.EVAL_BODY_INCLUDE;
    } else {
      return TagSupport.SKIP_BODY;
    }
  }

  boolean checkAuthorisation(FormPermissionAdapter tpa) {
    return SecurityUtils.getSubject().isPermitted(tpa);
  }
}
