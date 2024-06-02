package com.axiope.webapp.taglib;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.permissions.FormPermissionAdapter;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.TestFactory;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FormPermissionTagTest {
  FormPermissionTagTSS tag;

  class FormPermissionTagTSS extends FormPermissionTag {
    boolean isAuthorized;

    boolean checkAuthorisation(FormPermissionAdapter tpa) {
      return isAuthorized;
    }
  }

  @Before
  public void setUp() throws Exception {
    tag = new FormPermissionTagTSS();
  }

  @After
  public void tearDown() throws Exception {}

  @Test(expected = IllegalStateException.class)
  public void testDoStartTag() throws JspException {
    // throws ISE if attributes are not set
    tag.doStartTag();
  }

  @Test
  public void testDoStartTagOK() throws JspException {
    tag.setAction("READ");
    RSForm t = TestFactory.createAnyForm();
    tag.setForm(t);
    tag.isAuthorized = false;

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    // include body if authroized
    tag.isAuthorized = true;

    assertEquals(TagSupport.EVAL_BODY_INCLUDE, tag.doStartTag());
  }
}
