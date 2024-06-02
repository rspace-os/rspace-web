package com.axiope.webapp.taglib;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UserHasCollabGroupTest {

  User u;
  UserHasCollaborationGroup tag = new UserHasCollaborationGroup();

  @Before
  public void setUp() throws Exception {
    u = TestFactory.createAnyUser("any");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testDoStartTag() throws JspException {
    tag.setUser(u);
    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    Group g = new Group("any", u);
    g.addMember(u, RoleInGroup.DEFAULT);
    g.setGroupType(GroupType.LAB_GROUP);
    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());
    // include if has collab group
    g.setGroupType(GroupType.COLLABORATION_GROUP);
    assertEquals(TagSupport.EVAL_BODY_INCLUDE, tag.doStartTag());
  }
}
