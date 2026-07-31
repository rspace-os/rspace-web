package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.permissions.FormPermissionAdapter;
import com.researchspace.model.record.RSForm;
import com.researchspace.testutils.TestFactory;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FormPermissionTagTest {
  FormPermissionTagTSS tag;

  class FormPermissionTagTSS extends FormPermissionTag {
    boolean isAuthorized;

    boolean checkAuthorisation(FormPermissionAdapter tpa) {
      return isAuthorized;
    }
  }

  @BeforeEach
  public void setUp() throws Exception {
    tag = new FormPermissionTagTSS();
  }

  @Test
  public void testDoStartTag() throws JspException {
    assertThrows(
        IllegalStateException.class,
        () ->
            // throws ISE if attributes are not set
            tag.doStartTag());
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
