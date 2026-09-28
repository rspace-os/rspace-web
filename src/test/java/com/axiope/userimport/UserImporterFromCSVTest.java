package com.axiope.userimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.dto.CommunityPublicInfo;
import com.researchspace.model.dto.UserRegistrationInfo;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.model.field.ErrorList;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

public class UserImporterFromCSVTest {

  // TSS to provide always OK username
  private static class UserValidatorTSS extends UserValidator {
    public String validateUsername(String username) {
      return UserValidator.FIELD_OK;
    }
  }

  private UserImporterFromCSV importer;
  private UserLineParserFromCSV userLineParser;

  @Test
  public void writeFile() throws IOException {
    String base = RandomStringUtils.randomAlphabetic(8);
    File out = new File("jmeterusers.csv");
    FileOutputStream fos = new FileOutputStream(out);
    File out2 = new File("logins.csv");
    FileOutputStream fos2 = new FileOutputStream(out2);
    for (int i = 0; i < 300; i++) {
      String unique = base + i;
      StringBuffer sb = new StringBuffer();
      sb.append(unique)
          .append(", ")
          .append(unique)
          .append("sur")
          .append(", ")
          .append(unique)
          .append("@jmeter.com")
          .append(", ")
          .append("ROLE_USER")
          .append(", ")
          .append(unique)
          .append(",")
          .append("user1234")
          .append("\n");
      IOUtils.write(sb.toString(), fos);
      IOUtils.write(unique + ",user1234\n", fos2);
    }
  }

  @Test
  public void testAcceptanceOfRoles() {
    String OK1 = "Fred, Blogs, fbloggs@gmail.com, ROLE_USER,,";
    String OK2 = "Fred, Blogs, fbloggs@gmail.com, ROLE_PI,,";
    String OK3 = "Fred, Blogs, fbloggs@gmail.com, ROLE_ADMIN,,";
    String OK4 = "Fred, Blogs, fbloggs@gmail.com, ROLE_SYSADMIN,,";
    String[] TO_TEST = new String[] {OK1, OK2, OK3, OK4};
    for (String roleTest : TO_TEST) {
      UserImportResult result = parseCSVLines(roleTest);
      assertFalse(result.hasErrors(), roleTest + " failed ");
    }
    String INVALID_ROLE = "Fred, Blogs, fbloggs@gmail.com, ROLE_XXX,,";
    UserImportResult result = parseCSVLines(INVALID_ROLE);
    assertTrue(result.hasErrors(), INVALID_ROLE + " failed ");

    String INVALID_ROLE2 = "Fred, Blogs, fbloggs@gmail.com,S,,";
    UserImportResult users8 = parseCSVLines(INVALID_ROLE2);
    assertThat(users8.getParsedUsers()).isEmpty();
    assertTrue(users8.getErrors().hasErrorMessages());
    String errorMsg = users8.getErrors().getErrorMessages().get(0);
    assertEquals("system.csvImport.user.unrecognisedRole", errorMsg);
  }

  @Test
  public void testGetUsersToSignup() {
    String OK = "Fred, Blogs, fbloggs@gmail.com, ROLE_USER,,";
    List<UserRegistrationInfo> users = parseCSVLines(OK).getParsedUsers();

    assertThat(users).hasSize(1);
    UserRegistrationInfo okUser = users.iterator().next();
    assertThat(okUser.getUsername()).contains("fblogs");

    String OK_BLANK = OK + "\n \n	 \n" + OK;
    UserImportResult okResults = parseCSVLines(OK_BLANK);
    assertThat(okResults.getParsedUsers()).hasSize(2);
    assertFalse(okResults.getErrors().hasErrorMessages());

    String DUPLICATE_WITH_COMMENT =
        "## Following lines contain duplicate users.\n" + OK + "\n" + OK;
    UserImportResult okResults2 = parseCSVLines(DUPLICATE_WITH_COMMENT);
    assertThat(okResults2.getParsedUsers()).hasSize(2);
    assertFalse(okResults2.getErrors().hasErrorMessages());

    // if username is valid and specified, it is unchanged:
    String VALID = "Fred, Blogs, fbloggs@gmail.com, ROLE_USER,uName12,pwd12345";
    List<UserRegistrationInfo> user2 = parseCSVLines(VALID).getParsedUsers();
    assertEquals("uName12", user2.iterator().next().getUsername());

    // if uname is specified and duplicated, it will be a unique username
    String COMPLETE = "Fred, Blogs, fbloggs@gmail.com, ROLE_USER,uname,";
    String COMPLETE_DUPLICATE = COMPLETE + "\n" + COMPLETE;
    List<UserRegistrationInfo> users3 = parseCSVLines(COMPLETE_DUPLICATE).getParsedUsers();
    assertThat(users3).hasSize(2);

    String NO_EMAIL = "Fred, Blogs,, ROLE_USER,,";
    UserImportResult noEmailUser = parseCSVLines(NO_EMAIL);
    assertThat(noEmailUser.getParsedUsers()).hasSize(1);

    String NO_ROLE = "Fred, Blogs, fbloggs@gmail.com,,,";
    UserImportResult noRoleUser = parseCSVLines(NO_ROLE);
    assertThat(noRoleUser.getParsedUsers()).hasSize(1);

    String ONLY_5_FIELDS = "Fred, Blogs, fbloggs@gmail.com, ROLE_USER,";
    List<UserRegistrationInfo> invalidUsers = parseCSVLines(ONLY_5_FIELDS).getParsedUsers();
    assertThat(invalidUsers).isEmpty();

    String NO_USERNAME_AND_NO_INFO_TO_MAKE_ONE = " , , fbloggs@gmail.com, ROLE_USER,,";
    UserImportResult invalidUsers2 = parseCSVLines(NO_USERNAME_AND_NO_INFO_TO_MAKE_ONE);
    assertThat(invalidUsers2.getParsedUsers()).isEmpty();
    assertTrue(invalidUsers2.getErrors().hasErrorMessages());
  }

  @Test
  public void testParseGroups() {
    String line = "#Groups\n" + ","; // no display name
    UserImportResult users5 = parseCSVLines(line);
    assertTrue(users5.getErrors().hasErrorMessages());

    String line2 = "#Groups\n" + "groupname,"; // no display name
    UserImportResult grp2 = parseCSVLines(line2);
    assertTrue(grp2.getErrors().hasErrorMessages());

    String line3 =
        "xxx,xxx,xxx@x.com,ROLE_PI,xxxxxx,\n#Groups\n" + "groupname,xxxxxx"; // no display name
    UserImportResult grp3 = parseCSVLines(line3);
    assertFalse(grp3.getErrors().hasErrorMessages());
  }

  @Test
  public void testCommunityLineValidation() {
    String line = "#Communities\n" + "community"; // needs two fields
    UserImportResult result = parseCSVLines(line);

    ErrorList errors = result.getErrors();
    assertTrue(errors.hasErrorMessages());
    assertThat(errors.getErrorMessages())
        .element(0)
        .isEqualTo("system.csvImport.community.wrongNumberOfFields");

    String line2 = "#Communities\n" + "community, unknown lab"; // unknown labGroup name
    UserImportResult result2 = parseCSVLines(line2);

    ErrorList errors2 = result2.getErrors();
    assertTrue(errors2.hasErrorMessages());
    assertThat(errors2.getErrorMessages()).element(0).isEqualTo("system.csvImport.unknownLabGroup");
  }

  @Test
  public void testCommunityAdminsLineValidation() {
    String line = "#Community Admins\n" + "community"; // needs two fields
    UserImportResult result = parseCSVLines(line);

    ErrorList errors = result.getErrors();
    assertTrue(errors.hasErrorMessages());
    assertThat(errors.getErrorMessages())
        .element(0)
        .isEqualTo("system.csvImport.communityAdmin.wrongNumberOfFields");

    line = "#Community Admins\n" + "community, unknownAdmin"; // unknown admin
    result = parseCSVLines(line);

    errors = result.getErrors();
    assertTrue(errors.hasErrorMessages());
    assertThat(errors.getErrorMessages()).element(0).isEqualTo("system.csvImport.unknownUsername");
  }

  @Test
  public void testParseCommunityWithAdmin() {
    String line =
        "Fred, Blogs, fbloggs@gmail.com, ROLE_PI, testpi,\n"
            + "Fred, Blogs2, fbloggs2@gmail.com, ROLE_ADMIN, testadmin,\n"
            + "#Groups\n"
            + "test lab, testpi\n"
            + "#Communities\n"
            + "test community, test lab\n"
            + "#Community Admins\n"
            + "test community, testadmin";

    UserImportResult result = parseCSVLines(line);
    assertThat(result.getParsedCommunities()).hasSize(1);
    assertFalse(result.getErrors().hasErrorMessages());

    CommunityPublicInfo parsedCommunity = result.getParsedCommunities().get(0);
    assertEquals("test community", parsedCommunity.getDisplayName());
    assertThat(parsedCommunity.getUniqueName()).startsWith("testcommunity");
    assertThat(parsedCommunity.getAdmins()).element(0).isEqualTo("testadmin");
    assertThat(parsedCommunity.getLabGroups()).element(0).isEqualTo("test lab");
  }

  private UserImportResult parseCSVLines(String lines) {
    ByteArrayInputStream bais = new ByteArrayInputStream(lines.getBytes());
    setupImporter();
    return importer.getUsersToSignup(bais);
  }

  @Test
  public void testSSOBatchImportFile() throws IOException {
    InputStream logins =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("SSO_permissiveUnameImport.csv");
    setupImporter();
    RequireValidUserNameStrategy strategy = new RequireValidUserNameStrategy();
    strategy.setValidator(new UserValidatorTSS());
    userLineParser.setUnamecreationStrategy(strategy);
    UserImportResult users = importer.getUsersToSignup(logins);

    assertFalse(users.getErrors().hasErrorMessages());
    assertThat(users.getParsedGroups()).hasSize(3); // 3 groups formed.
  }

  private void setupImporter() {
    userLineParser = new UserLineParserFromCSV();
    userLineParser.setUnamecreationStrategy(new UserNameFromFirstLastNameStrategy());

    PropertyHolder propertyHolder = new PropertyHolder();
    propertyHolder.setStandalone("true");
    userLineParser.setProperties(propertyHolder);

    ResourceBundleMessageSource msgSource = new ResourceBundleMessageSource();
    msgSource.setUseCodeAsDefaultMessage(true);
    MessageSourceUtils messages = new MessageSourceUtils(msgSource);
    userLineParser.setMessages(messages);

    importer = new UserImporterFromCSV();
    importer.setUserLineParser(userLineParser);
    importer.setMessages(messages);
  }
}
