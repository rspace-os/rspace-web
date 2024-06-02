package com.axiope.userimport;

import com.researchspace.Constants;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.dto.CommunityPublicInfo;
import com.researchspace.model.dto.GroupPublicInfo;
import com.researchspace.model.dto.UserRegistrationInfo;
import com.researchspace.model.field.ErrorList;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.shiro.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Basic importer from a text CSV file: There must be 6 fields in the order: Firstname, Lastname,
 * email, Role, username,password
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UserImporterFromCSV implements UserListGenerator {

  @Autowired private MessageSource messages;

  @Autowired private UserLineParserFromCSV userLineParser;

  private static Logger log = LoggerFactory.getLogger(UserImporterFromCSV.class);

  private static final int EXPECTED_MIN_GROUP_FIELDS = 2; // need at least group name and single pi
  private static final int EXPECTED_MIN_COMMUNITY_FIELDS = 2; // need at least name and a single lab
  private static final int EXPECTED_MIN_COMMUNITY_ADMIN_FIELDS =
      2; // need at least name and a single admin

  private static final int USER_MODE = 1;
  private static final int GROUP_MODE = 2;
  private static final int COMMUNITY_MODE = 3;
  private static final int COMMUNITY_ADMIN_MODE = 4;

  private int parseMode = USER_MODE;

  @Override
  public UserImportResult getUsersToSignup(InputStream inputStream) {
    Validate.notNull(inputStream);

    List<UserRegistrationInfo> users = new ArrayList<>();
    List<GroupPublicInfo> groups = new ArrayList<>();
    List<CommunityPublicInfo> communities = new ArrayList<>();
    ErrorList errors = new ErrorList();

    Set<String> seenUsernames = new HashSet<>();
    Map<String, UserRegistrationInfo> seenUsersMap = new HashMap<>();
    Map<String, GroupPublicInfo> seenGroupsMap = new HashMap<>();

    List<String> lines;
    try {
      lines = IOUtils.readLines(inputStream);
    } catch (UncheckedIOException e) {
      log.error("error on reading input stream", e);
      return null;
    }

    int lineNumber = 0;
    for (String line : lines) {
      log.debug("Parsing " + line);
      lineNumber++;

      if (StringUtils.isBlank(line) || line.startsWith("##")) {
        continue; // ignore empty lines and comments
      }

      if (isParseModeLine(line)) {
        parseMode = getParseModeFromLine(line);
        continue;
      }

      if (parseMode == USER_MODE) {
        UserRegistrationInfo userRegInfo = parseUserLine(line, lineNumber, errors, seenUsernames);
        if (userRegInfo != null) {
          log.debug("successfully parsed user: " + userRegInfo);
          users.add(userRegInfo);
          seenUsersMap.put(userRegInfo.getUsername(), userRegInfo);
        }
      } else if (parseMode == GROUP_MODE) {
        Group g = parseGroupLine(line, lineNumber, errors, seenUsersMap);
        if (g != null) {
          log.debug("successfully parsed group: " + g);
          GroupPublicInfo groupInfo = g.toPublicInfo();
          groups.add(groupInfo);
          seenGroupsMap.put(groupInfo.getDisplayName(), groupInfo);
        }
      } else if (parseMode == COMMUNITY_MODE) {
        CommunityPublicInfo commInfo = parseCommunityLine(line, lineNumber, errors, seenGroupsMap);
        if (commInfo != null) {
          log.debug("successfully parsed community: " + commInfo);
          addInfoToCommunitiesList(commInfo, communities);
        }
      } else if (parseMode == COMMUNITY_ADMIN_MODE) {
        CommunityPublicInfo commInfo =
            parseCommunityAdminLine(line, lineNumber, errors, seenUsersMap);
        if (commInfo != null) {
          log.debug("successfully parsed community admin: " + commInfo);
          addInfoToCommunitiesList(commInfo, communities);
        }
      }
    }

    this.parseMode = USER_MODE; // for next use

    return new UserImportResult(users, groups, communities, errors);
  }

  private boolean isParseModeLine(String line) {
    return getParseModeFromLine(line) != -1;
  }

  private int getParseModeFromLine(String line) {
    if ("#Users".equals(line)) {
      return USER_MODE;
    }
    if ("#Groups".equals(line)) {
      return GROUP_MODE;
    }
    if ("#Communities".equals(line)) {
      return COMMUNITY_MODE;
    }
    if ("#Community Admins".equals(line)) {
      return COMMUNITY_ADMIN_MODE;
    }
    return -1;
  }

  private UserRegistrationInfo parseUserLine(
      String line, int lineNumber, ErrorList errors, Set<String> seenUsernames) {
    UserRegistrationInfo userRegInfo = new UserRegistrationInfo();
    String userParsingMsg =
        userLineParser.populateUserInfo(userRegInfo, line, lineNumber, seenUsernames);
    if (userParsingMsg != null) {
      addToErrorListAndLog(errors, userParsingMsg);
      return null;
    }
    return userRegInfo;
  }

  private Group parseGroupLine(
      String line, int lineNumber, ErrorList errors, Map<String, UserRegistrationInfo> userMap) {

    String[] els = line.split(",");
    if (els.length < EXPECTED_MIN_GROUP_FIELDS) {
      addToErrorListAndLog(errors, createMinimalGroupInfoMissingMsg(line, lineNumber));
      return null;
    }

    if (StringUtils.isBlank(els[0])) {
      addToErrorListAndLog(errors, blankGroupNameErrorMsg(lineNumber));
      return null; // cannot manage without a group name
    }

    Group grp = new Group();
    grp.setDisplayName(els[0].trim());
    grp.createAndSetUniqueGroupName();

    if (!StringUtils.isBlank(els[1])) {
      String pi = els[1].trim();
      UserRegistrationInfo piInfo = userMap.get(pi);

      if (piInfo == null) {
        addToErrorListAndLog(errors, unknownUsernameMsg(pi, lineNumber));
        return null; // user pointed as PI not on import list
      } else if (!Role.PI_ROLE.getName().equals(piInfo.getRole())) {
        addToErrorListAndLog(errors, suggestedPINotAssignedPIRole(pi, lineNumber));
        return null; // user pointed as PI not having PI role
      }
      grp.setPis(pi);

    } else {
      addToErrorListAndLog(errors, blankPINameErrorMsg(lineNumber));
      return null; // cannot manage without a PI
    }
    // validate members
    List<String> members = new ArrayList<String>();
    for (int i = 1; i < els.length; i++) {
      if (StringUtils.isBlank(els[i])) {
        addToErrorListAndLog(errors, blankUsernameErrorMsg(lineNumber));
        return null;
      }

      String member = els[i].trim();
      if (userMap.get(member) == null) {
        addToErrorListAndLog(errors, unknownUsernameMsg(member, lineNumber));
        return null; // user not on import list
      }
      members.add(member);
    }
    grp.setMemberString(members);

    return grp;
  }

  private CommunityPublicInfo parseCommunityLine(
      String line, int lineNumber, ErrorList errors, Map<String, GroupPublicInfo> groupsMap) {

    String[] els = line.split(",");
    if (els.length < EXPECTED_MIN_COMMUNITY_FIELDS) {
      addToErrorListAndLog(errors, createMinimalCommunityInfoMissingMsg(line, lineNumber));
      return null;
    }

    // validate labGroups
    List<String> labGroups = new ArrayList<String>();
    for (int i = 1; i < els.length; i++) {
      if (StringUtils.isBlank(els[i])) {
        addToErrorListAndLog(errors, blankGroupNameErrorMsg(lineNumber));
        return null;
      }

      String groupName = els[i].trim();
      GroupPublicInfo group = groupsMap.get(groupName);

      if (group == null) {
        addToErrorListAndLog(errors, unknownLabGroupMsg(groupName, lineNumber));
        return null;
      }
      labGroups.add(groupName);
    }

    CommunityPublicInfo community = getCommunityPublicInfo(els[0].trim());
    community.setLabGroups(labGroups);

    return community;
  }

  private CommunityPublicInfo parseCommunityAdminLine(
      String line, int lineNumber, ErrorList errors, Map<String, UserRegistrationInfo> userMap) {

    String[] els = line.split(",");
    if (els.length < EXPECTED_MIN_COMMUNITY_ADMIN_FIELDS) {
      addToErrorListAndLog(errors, createMinimalCommunityAdminInfoMissingMsg(line, lineNumber));
      return null;
    }

    // validate admins
    List<String> admins = new ArrayList<String>();
    for (int i = 1; i < els.length; i++) {
      if (StringUtils.isBlank(els[i])) {
        addToErrorListAndLog(errors, blankUsernameErrorMsg(lineNumber));
        return null; // blank name listed as group member
      }

      String username = els[i].trim();
      UserRegistrationInfo user = userMap.get(username);

      if (user == null) {
        addToErrorListAndLog(errors, unknownUsernameMsg(username, lineNumber));
        return null; // user not on import list
      } else if (!Constants.ADMIN_ROLE.equals(user.getRole())
          && !Constants.SYSADMIN_ROLE.equals(user.getRole())) {
        addToErrorListAndLog(
            errors, suggestedCommunityAdminNotAssignedAdminRole(username, lineNumber));
        return null;
      }
      admins.add(username);
    }

    CommunityPublicInfo community = getCommunityPublicInfo(els[0].trim());
    community.setAdmins(admins);

    return community;
  }

  private CommunityPublicInfo getCommunityPublicInfo(String communityName) {
    CommunityPublicInfo community = new CommunityPublicInfo();
    community.setDisplayName(communityName);
    community.setUniqueName(Community.createUniqueName(communityName));

    return community;
  }

  private void addInfoToCommunitiesList(
      CommunityPublicInfo commInfo, List<CommunityPublicInfo> communities) {

    String communityName = commInfo.getDisplayName();
    boolean foundExisting = false;
    for (CommunityPublicInfo existingCommunity : communities) {
      if (communityName.equals(existingCommunity.getDisplayName())) {
        foundExisting = true;
        if (!CollectionUtils.isEmpty(commInfo.getAdmins())) {
          existingCommunity.setAdmins(commInfo.getAdmins());
        }
        if (!CollectionUtils.isEmpty(commInfo.getLabGroups())) {
          existingCommunity.setAdmins(commInfo.getLabGroups());
        }
      }
    }
    if (!foundExisting) {
      communities.add(commInfo);
    }
  }

  private void addToErrorListAndLog(ErrorList el, String errorMessage) {
    el.addErrorMsg(errorMessage);
    log.warn(errorMessage);
  }

  private String suggestedPINotAssignedPIRole(String pi, int lineNumber) {
    return messages.getMessage(
        "system.csvimport.group.piWithoutPiRole", new Object[] {pi, lineNumber}, null);
  }

  private String suggestedCommunityAdminNotAssignedAdminRole(String admin, int lineNumber) {
    return messages.getMessage(
        "system.csvimport.communityAdmin.noAdminRole", new Object[] {admin, lineNumber}, null);
  }

  private String unknownUsernameMsg(String name, int lineNumber) {
    return messages.getMessage(
        "system.csvimport.unknownUsername", new Object[] {name, lineNumber}, null);
  }

  private String unknownLabGroupMsg(String name, int lineNumber) {
    return messages.getMessage(
        "system.csvimport.unknownLabGroup", new Object[] {name, lineNumber}, null);
  }

  private String blankUsernameErrorMsg(int lineNumber) {
    return messages.getMessage("system.csvimport.blankUsername", new Object[] {lineNumber}, null);
  }

  private String blankPINameErrorMsg(int lineNumber) {
    return messages.getMessage("system.csvimport.group.blankPI", new Object[] {lineNumber}, null);
  }

  private String blankGroupNameErrorMsg(int lineNumber) {
    return messages.getMessage(
        "system.csvimport.group.blankGroupName", new Object[] {lineNumber}, null);
  }

  private String createMinimalGroupInfoMissingMsg(String line, int lineNumber) {
    return messages.getMessage(
        "system.csvimport.group.wrongNumberOfFields", new Object[] {line, lineNumber}, null);
  }

  private String createMinimalCommunityInfoMissingMsg(String line, int lineNumber) {
    return messages.getMessage(
        "system.csvimport.community.wrongNumberOfFields", new Object[] {line, lineNumber}, null);
  }

  private String createMinimalCommunityAdminInfoMissingMsg(String line, int lineNumber) {
    return messages.getMessage(
        "system.csvimport.communityAdmin.wrongNumberOfFields",
        new Object[] {line, lineNumber},
        null);
  }

  /*
   * ===============
   *  for tests
   * ===============
   */

  public void setMessages(MessageSource messages) {
    this.messages = messages;
  }

  public void setUserLineParser(UserLineParserFromCSV userLineParser) {
    this.userLineParser = userLineParser;
  }
}
