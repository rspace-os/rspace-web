package com.axiope.webapp.taglib;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.service.impl.ShiroTestUtils;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import org.apache.shiro.subject.Subject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class IsRunAsTest {
  static ShiroTestUtils shiroUtils;
  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock Subject subject;
  IsRunAs isRunAsTag;

  @Before
  public void setUp() throws Exception {
    shiroUtils = new ShiroTestUtils();
    shiroUtils.setSubject(subject);
    isRunAsTag = new IsRunAs();
  }

  @After
  public void tearDown() throws Exception {
    shiroUtils.clearSubject();
  }

  @Test
  public void testDoStartTag() throws JspException {
    when(subject.isRunAs()).thenReturn(true, false);
    assertEquals(TagSupport.EVAL_BODY_INCLUDE, isRunAsTag.doStartTag());
    assertEquals(TagSupport.SKIP_BODY, isRunAsTag.doStartTag());
  }
}
