package com.axiope.userimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.researchspace.model.dto.UserRegistrationInfo;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.properties.PropertyHolder;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

public class UserLineParserFromCSVTest {

  private UserLineParserFromCSV lineParser;
  private IMutablePropertyHolder properties;

  private String USER_LINE_VALID_STANDALONE =
      "Fred, Blogs, fbloggs@gmail.com, ROLE_USER, user1, testPass";
  private String USER_LINE_VALID_CLOUD =
      "Fred, Blogs, fbloggs@gmail.com, Univeristy of Qwerty, ROLE_USER, user1, testPass";
  private String USER_LINE_VALID_SSO = "Fred, Blogs, fbloggs@gmail.com, ROLE_USER, user1";

  @Before
  public void setUp() throws Exception {
    lineParser = new UserLineParserFromCSV();
    lineParser.setUnamecreationStrategy(new UserNameFromFirstLastNameStrategy());

    ResourceBundleMessageSource msgSource = new ResourceBundleMessageSource();
    msgSource.setUseCodeAsDefaultMessage(true);
    lineParser.setMessages(msgSource);

    properties = new PropertyHolder();
    lineParser.setProperties(properties);
  }

  @Test
  public void testImportStandaloneUser() throws IOException {

    properties.setStandalone("true");
    properties.setCloud("false");

    Set<String> seenUsernames = new HashSet<>();
    String result = lineParser.populateUserInfo(null, USER_LINE_VALID_CLOUD, 1, seenUsernames);
    assertEquals("system.csvimport.user.wrongNumberOfFields", result);
    String result2 = lineParser.populateUserInfo(null, USER_LINE_VALID_SSO, 1, seenUsernames);
    assertEquals("system.csvimport.user.wrongNumberOfFields", result2);

    UserRegistrationInfo tempUser = new UserRegistrationInfo();
    String result3 =
        lineParser.populateUserInfo(tempUser, USER_LINE_VALID_STANDALONE, 1, seenUsernames);
    assertNull(result3);

    assertNotNull(tempUser.getUsername());
    assertNotNull(tempUser.getPassword());
    assertNull(tempUser.getAffiliation());
  }

  @Test
  public void testImportSSOUser() throws IOException {

    properties.setStandalone("false");
    properties.setCloud("false");

    Set<String> seenUsernames = new HashSet<>();
    String result = lineParser.populateUserInfo(null, USER_LINE_VALID_STANDALONE, 1, seenUsernames);
    assertEquals("system.csvimport.user.wrongNumberOfFields", result);
    String result2 = lineParser.populateUserInfo(null, USER_LINE_VALID_CLOUD, 1, seenUsernames);
    assertEquals("system.csvimport.user.wrongNumberOfFields", result2);

    UserRegistrationInfo tempUser = new UserRegistrationInfo();
    String result3 = lineParser.populateUserInfo(tempUser, USER_LINE_VALID_SSO, 1, seenUsernames);
    assertNull(result3);

    assertNotNull(tempUser.getUsername());
    assertNull(tempUser.getPassword());
    assertNull(tempUser.getAffiliation());
  }

  @Test
  public void testImportCloudUser() throws IOException {

    properties.setStandalone("true");
    properties.setCloud("true");

    Set<String> seenUsernames = new HashSet<>();
    String result = lineParser.populateUserInfo(null, USER_LINE_VALID_STANDALONE, 1, seenUsernames);
    assertEquals("system.csvimport.user.wrongNumberOfFields", result);
    String result2 = lineParser.populateUserInfo(null, USER_LINE_VALID_SSO, 1, seenUsernames);
    assertEquals("system.csvimport.user.wrongNumberOfFields", result2);

    UserRegistrationInfo tempUser = new UserRegistrationInfo();
    String result3 = lineParser.populateUserInfo(tempUser, USER_LINE_VALID_CLOUD, 1, seenUsernames);
    assertNull(result3);

    assertNotNull(tempUser.getUsername());
    assertNotNull(tempUser.getPassword());
    assertNotNull(tempUser.getAffiliation());
  }
}
