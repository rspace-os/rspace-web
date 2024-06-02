package com.axiope.webapp.taglib;

import com.researchspace.auth.PermissionUtils;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RSForm;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * Wraps the argument template as a permissions and includes body text if permission is satisfied.
 */
public class RecordPermissionTag extends TagSupport {

  private static final long serialVersionUID = -2545513925674998973L;
  private BaseRecord record;
  private String action;
  private User user;

  /**
   * The current subject
   *
   * @return
   */
  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  /**
   * Compulsory attribute; should be a String representation of a {@link PermissionType}
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
  public BaseRecord getRecord() {
    return record;
  }

  public void setRecord(BaseRecord record) {
    this.record = record;
  }

  /**
   * @throws JspException if template or action is null
   */
  public int doStartTag() throws JspException {
    if (action == null || record == null || user == null) {
      throw new IllegalStateException(" template or action is null");
    }

    BaseRecord toShare = getRecord();
    // this prevents ability to share folder or media record in UI
    PermissionType pt = PermissionType.valueOf(action);
    if (PermissionType.SHARE.equals(pt)
        && (isFolderButNotNotebook(toShare)
            || toShare.isSnippet()
            || toShare.isMediaRecord()
            ||
            // hack to prevent sharing  by non-owner until we can fix permissions.
            !toShare.getOwner().equals(user))) {
      return TagSupport.SKIP_BODY;
    }
    if (PermissionType.PUBLISH.equals(pt)
        && (isFolderButNotNotebook(toShare)
            || toShare.isPublished()
            || toShare.isSnippet()
            || toShare.isMediaRecord())) {
      return TagSupport.SKIP_BODY;
    }

    boolean show = checkAuthorisation(toShare, pt);

    if (show) {
      return TagSupport.EVAL_BODY_INCLUDE;
    } else {
      return TagSupport.SKIP_BODY;
    }
  }

  private boolean isFolderButNotNotebook(BaseRecord toShare) {
    return toShare.isFolder() && !toShare.isNotebook();
  }

  boolean checkAuthorisation(BaseRecord toShare, PermissionType pt) {
    PermissionUtils pu = new PermissionUtils();
    boolean show = pu.isPermitted(toShare, pt, user);
    return show;
  }
}
