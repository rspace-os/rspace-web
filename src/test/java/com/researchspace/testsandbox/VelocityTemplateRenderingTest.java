package com.researchspace.testsandbox;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.VelocityTestUtils;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Before;
import org.junit.Test;

public class VelocityTemplateRenderingTest {

  private static final String GROUP_INVITATION_NEW_USER =
      "MessageAndNotificationCloud/groupInvitationNewUser";
  private static final String ACCOUNT_OPERATIONS_TEMPLATES =
      "src/main/resources/velocityTemplates/";
  VelocityEngine velocityEngine = null;
  Map<String, Object> data = new HashMap<>();

  @Before
  public void before() {
    velocityEngine = VelocityTestUtils.setupVelocity(ACCOUNT_OPERATIONS_TEMPLATES);
  }

  @Test
  public void testPasswordReset() {
    data.put("genuineEmail", true);
    data.put("resetLink", "http://somewhere.com");
    data.put("ipAddress", "121.34.54.23");
    VelocityContext velocityContext = new VelocityContext(data);
    StringWriter sw = new StringWriter();
    velocityEngine.mergeTemplate(
        "accountOperations/passwordResetMessage-plaintext.vm", "UTF-8", velocityContext, sw);
    System.err.println(sw.toString());
    sw = new StringWriter();
    System.err.println("HTML:");
    velocityEngine.mergeTemplate(
        "accountOperations/passwordResetMessage.vm", "UTF-8", velocityContext, sw);
    System.err.println(sw.toString());
  }

  @Test
  public void testCloudGroupInvitationNewUser() {
    User creator = TestFactory.createAnyUserWithRole("pi", Role.PI_ROLE.getName());
    Group group = TestFactory.createAnyGroup(creator, new User[] {});
    data.put("creator", creator);
    data.put("group", group);
    VelocityContext velocityContext = new VelocityContext(data);
    StringWriter sw = new StringWriter();
    velocityEngine.mergeTemplate(
        GROUP_INVITATION_NEW_USER + "-plaintext.vm", "UTF-8", velocityContext, sw);
    System.err.println(sw.toString());
    sw = new StringWriter();
    System.err.println("HTML:");
    velocityEngine.mergeTemplate(GROUP_INVITATION_NEW_USER + ".vm", "UTF-8", velocityContext, sw);
    System.err.println(sw.toString());
  }

  @Test
  public void rawImageSrc() {
    data =
        Map.of(
            "fieldId",
            22,
            "itemId",
            13,
            "width",
            23,
            "height",
            44,
            "rotation",
            0,
            "milliseconds",
            "48554398");
    var velocityContext = new VelocityContext(data);
    var sw = new StringWriter();
    velocityEngine.mergeTemplate("textFieldElements/rawImageLink.vm", "UTF-8", velocityContext, sw);
    assertTrue(
        sw.toString()
            .contains(
                "/thumbnail/data?sourceType=IMAGE&sourceId=13&sourceParentId=22&width=23&height=44&rotation=0&time=48554398"));
  }
}
