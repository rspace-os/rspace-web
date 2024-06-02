package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveImportScope;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.archive.model.ArchiveUsers;
import com.researchspace.core.util.XMLReadWriteUtils;
import com.researchspace.licensews.LicenseExceededException;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.UserProfile;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.IGroupCreationStrategy;
import com.researchspace.service.LicenseRequestResult;
import com.researchspace.service.LicenseService;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.UserManager;
import com.researchspace.service.UserProfileManager;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

public class UserImporterImpl implements UserImporter {

  private @Autowired CommunityServiceManager communityMgr;
  private @Autowired GroupManager grpMgr;
  private @Autowired IGroupCreationStrategy groupCreator;
  private @Autowired LicenseService licenseService;
  private @Autowired IContentInitializer contentInitializer;
  private @Autowired UserManager userMgr;
  private @Autowired UserProfileManager userProfileManager;

  Logger log = org.slf4j.LoggerFactory.getLogger(UserImporterImpl.class);

  public void createUsers(
      IArchiveModel model, ArchivalImportConfig iconfig, User importer, ImportArchiveReport report)
      throws Exception {
    // don't bother trying to import.
    if (ArchiveImportScope.IGNORE_USERS_AND_GROUPS.equals(iconfig.getScope())) {
      log.info("Ignoring any user data, since import config is {}", iconfig.getScope());
      return;
    }
    File usersXMLFile = model.getUserInfo();
    if (usersXMLFile == null || !usersXMLFile.exists()) {
      log.warn("No user information  records found");
      return;
    }
    ArchiveUsers fromXml = XMLReadWriteUtils.fromXML(usersXMLFile, ArchiveUsers.class, null, null);
    saveArchiveUsersToDatabase(importer, fromXml, report);
  }

  @Override
  public void saveArchiveUsersToDatabase(
      User importer, ArchiveUsers fromXml, ImportArchiveReport report) throws UserExistsException {
    // these users and groups have random names, so will be unique - we can
    // import them new.
    // prefs are saved by cascade
    Map<User, User> userArchive2Database = new HashMap<>();
    Set<User> alreadyExists = new HashSet<>();
    for (User archiveUser : fromXml.getUsers()) {
      archiveUser.setPassword("newuser");
      Role role = archiveUser.getRoles().iterator().next();
      archiveUser.setRole(role.getName());
      archiveUser.setRoles(new HashSet<Role>());
      if (userMgr.userExists(archiveUser.getUsername())) {
        report
            .getInfoList()
            .addErrorMsg(" User [" + archiveUser.getUsername() + "] already exists; skipping");
        alreadyExists.add(archiveUser);
        continue;
      }
      LicenseRequestResult result = licenseService.requestUserLicenses(1, role);
      if (!result.isRequestOK() && result.isLicenseServerAvailable()) {
        throw new LicenseExceededException(
            "There are insufficient license seats to import all the new users listed in this"
                + " import");
      }
      User savedUser = userMgr.saveNewUser(archiveUser);
      // initialise folders
      contentInitializer.init(archiveUser.getId());
      userArchive2Database.put(archiveUser, savedUser);
    }
    // save profiles
    for (UserProfile profile : fromXml.getProfiles()) {
      if (!alreadyExists.contains(profile.getOwner())) {
        userProfileManager.saveUserProfile(profile);
      }
    }

    Map<Group, Group> groupArchive2Database = new HashMap<>();
    // now create and save groups.
    for (Group archiveGroup : fromXml.getGroups()) {
      if (grpMgr.groupExists(archiveGroup.getUniqueName())) {
        continue;
      }
      Group savedGroup =
          groupCreator.createAndSaveGroup(
              archiveGroup.getDisplayName(),
              archiveGroup.getPiusers().iterator().next(),
              importer,
              archiveGroup.getGroupType(),
              archiveGroup.getMembers().toArray(new User[archiveGroup.getMembers().size()]));
      savedGroup.setProfileText(archiveGroup.getProfileText());
      savedGroup = grpMgr.saveGroup(savedGroup, importer);
      groupArchive2Database.put(archiveGroup, savedGroup);
    }
    Community savedComm = null;
    // now create and save communities.
    for (Community archiveComm : fromXml.getCommunities()) {
      savedComm = new Community(archiveComm.getAdmins().iterator().next(), archiveComm);
      savedComm = communityMgr.save(savedComm);
      for (Group archiveGrp : archiveComm.getLabGroups()) {
        communityMgr.addGroupToCommunity(
            groupArchive2Database.get(archiveGrp).getId(), savedComm.getId(), importer);
      }
      Long[] adminIds = new Long[archiveComm.getAdmins().size()];
      int index = 0;
      for (User archiveAdmin : archiveComm.getAdmins()) {
        adminIds[index] = userArchive2Database.get(archiveAdmin).getId();
        communityMgr.addAdminsToCommunity(adminIds, savedComm.getId());
      }
    }
  }
}
