package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Snippet;
import com.researchspace.testutils.TestFactory;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RecordPermissionTagTest {
  RecordPermissionTagTSS tag;
  User user;

  class RecordPermissionTagTSS extends RecordPermissionTag {
    boolean isAuthorized;

    boolean checkAuthorisation(BaseRecord toShare, PermissionType pt) {
      return isAuthorized;
    }
  }

  @BeforeEach
  public void setUp() throws Exception {
    tag = new RecordPermissionTagTSS();
    user = TestFactory.createAnyUser("any");
    ;
  }

  @Test
  public void testDoStartTag() throws JspException {
    assertThrows(IllegalStateException.class, () -> tag.doStartTag());
  }

  @Test
  public void testDoStartTagOK() throws JspException {
    tag.setAction("READ");
    BaseRecord t = TestFactory.createAnySD();
    t.setOwner(user);

    tag.setRecord(t);
    tag.setUser(user);
    tag.isAuthorized = false;
    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    // include body if authroized
    tag.isAuthorized = true;
    assertEquals(TagSupport.EVAL_BODY_INCLUDE, tag.doStartTag());

    // check that folders can't be shared ( from 0.13 new requirment)
    BaseRecord rc = TestFactory.createAFolder("any", user);
    tag.setRecord(rc);
    tag.setAction("SHARE");
    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    // chekc that media records can't be shared either in ui
    EcatMediaFile media = TestFactory.createEcatAudio(1L, user);
    tag.setRecord(media);
    tag.setAction("SHARE");
    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    // or snippets RSPAC-999
    Snippet snip = TestFactory.createAnySnippet(user);
    tag.setRecord(snip);
    tag.setAction("SHARE");
    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    // check that non-owner can't share:
    User other = TestFactory.createAnyUser("other");
    tag.setUser(other);
    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());
  }
}
