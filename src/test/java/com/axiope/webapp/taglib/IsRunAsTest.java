package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.service.impl.ShiroTestUtils;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IsRunAsTest {
  static ShiroTestUtils shiroUtils;
  @Mock Subject subject;
  IsRunAs isRunAsTag;

  @BeforeEach
  public void setUp() throws Exception {
    shiroUtils = new ShiroTestUtils();
    shiroUtils.setSubject(subject);
    isRunAsTag = new IsRunAs();
  }

  @AfterEach
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
