package com.researchspace.service.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.comms.GroupMessageOrRequest;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.LocaleBoundMessages;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.TestFactory;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Before;
import org.junit.Test;

public class RequestEmailTemplateRenderTest {

  private VelocityEngine velocity;
  private MessageSourceUtils messages;

  @Before
  public void setUp() {
    velocity = new VelocityEngine();
    Properties props = new Properties();
    props.setProperty(
        "resource.loader.file.path",
        "src/main/resources/velocityTemplates/,"
            + "src/main/resources/velocityTemplates/messageAndNotificationEmails");
    props.setProperty("resource.loader", "file");
    props.setProperty("velocimacro.library", "VM_global_library.vm");
    velocity.init(props);

    messages = new MessageSourceUtils(new JsonMessageSource());
  }

  private Map<String, Object> baseModel(MessageOrRequest mor) {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", mor);
    model.put("baseURL", "http://localhost:8080");
    model.put("dateOb", new Date(0));
    model.put("date", new LocaleAwareDateTool(Locale.US));
    model.put("msg", new LocaleBoundMessages(messages, Locale.US));
    return model;
  }

  private String render(String template, Map<String, Object> model) {
    VelocityContext context = new VelocityContext(model);
    java.io.StringWriter writer = new java.io.StringWriter();
    velocity.mergeTemplate(template, "UTF-8", context, writer);
    return writer.toString();
  }

  @Test
  public void joinLabGroupRequestRendersCoherentSentences() {
    User originator = TestFactory.createAnyUser("originator");
    Group group = new Group();
    group.setDisplayName("Chemistry Lab");
    GroupMessageOrRequest mor = new GroupMessageOrRequest(MessageType.REQUEST_JOIN_LAB_GROUP);
    mor.setOriginator(originator);
    mor.setGroup(group);

    String html = render("request.vm", baseModel(mor));

    assertFalse(html.contains("???"));
    assertTrue(
        html.contains(
            "The RSpace Principal Investigator \"<b>" + originator.getFullName() + "</b>\""));
    assertTrue(html.contains("sent you a request on"));
    assertTrue(html.contains("In order to join the Lab Group \"<b>Chemistry Lab</b>\""));
  }

  @Test
  public void joinProjectGroupRequestRendersCoherentSentences() {
    User originator = TestFactory.createAnyUser("originator");
    Group group = new Group();
    group.setDisplayName("Battery Project");
    GroupMessageOrRequest mor = new GroupMessageOrRequest(MessageType.REQUEST_JOIN_PROJECT_GROUP);
    mor.setOriginator(originator);
    mor.setGroup(group);

    String html = render("request.vm", baseModel(mor));

    assertFalse(html.contains("???"));
    assertTrue(html.contains("The RSpace User \"<b>" + originator.getFullName() + "</b>\""));
    assertTrue(html.contains("In order to join the Project Group \"<b>Battery Project</b>\""));
  }

  @Test
  public void simpleRecordRequestPlaintextRendersCoherentSentences() {
    User originator = TestFactory.createAnyUser("originator");
    MessageOrRequest mor = TestFactory.createAnyMessage(originator);

    String text = EmailHtmlToPlainText.toPlainText(render("request.vm", baseModel(mor)));

    assertFalse(text.contains("???"));
    assertFalse(text.contains("<"));
    assertTrue(text.contains("RSpace Request from"));
    assertTrue(
        text.contains(
            "The RSpace User \"" + originator.getFullName() + "\" sent you a request on"));
  }
}
