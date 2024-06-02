package com.researchspace.dao.customliquibaseupdates.v41;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.customliquibaseupdates.AbstractCustomLiquibaseUpdater;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.service.FolderManager;
import java.util.Set;
import liquibase.database.Database;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;

/**
 * This class creates missing user folders in Shared -> Labroups locations for: 1) Lab Admins with
 * 'View All Docs' permission, who never had them (RSPAC-1088) 2) PIs who lost the folder after user
 * was removed from one of their groups (RSPAC-1113).
 */
public class CreateMissingUserFoldersForLabAdminsAndPIs extends AbstractCustomLiquibaseUpdater {

  private FolderManager folderMgr;

  private FolderDao folderDao;

  protected int affectedPiCounter = 0;
  protected int piFoldersCreatedCounter = 0;

  protected int affectedLabAdminCounter = 0;
  protected int labAdminFoldersCreatedCounter = 0;

  private String updateSuccessMessage = "...";

  @Override
  protected void addBeans() {
    folderMgr = context.getBean(FolderManager.class);
    folderDao = context.getBean("folderDao", FolderDao.class);
  }

  @Override
  protected void doExecute(Database database) {
    logger.info("Starting User Folders creationLooking for Lab Admins and PIs.");

    final ScrollableResults allGroups =
        sessionFactory
            .getCurrentSession()
            .createQuery("from Group")
            .scroll(ScrollMode.FORWARD_ONLY);

    while (allGroups.next()) {
      Group group = (Group) allGroups.get(0);
      if (group.isLabGroup()) {
        createMissingUserFoldersForGroup(group);
      }
    }

    updateSuccessMessage = "User folders creation complete. ";
    updateSuccessMessage +=
        String.format(
            "Created %d user folders in %d Lab Admin LabGroups locations, "
                + "and %d user folders in %d PIs LabGroups locations.",
            labAdminFoldersCreatedCounter,
            affectedLabAdminCounter,
            piFoldersCreatedCounter,
            affectedPiCounter);

    logger.info(updateSuccessMessage);
  }

  private void createMissingUserFoldersForGroup(Group group) {
    Set<User> nonPiMembers = group.getAllNonPIMembers();
    for (User pi : group.getPiusers()) {
      int createdPiFolders = createMissingUserFolders(pi, nonPiMembers);
      if (createdPiFolders > 0) {
        logger.info(
            String.format(
                "Created %d user folders for PI %s and group %s",
                createdPiFolders, pi.getUsername(), group.getDisplayName()));
        affectedPiCounter++;
        piFoldersCreatedCounter += createdPiFolders;
      }
    }

    Set<User> groupAdminsWithViewAll = group.getLabAdminsWithViewAllPermission();
    if (groupAdminsWithViewAll.size() < 2) {
      for (User labAdminWithViewAll : groupAdminsWithViewAll) {
        int createdLabAdminFolders = createMissingUserFolders(labAdminWithViewAll, nonPiMembers);
        if (createdLabAdminFolders > 0) {
          logger.info(
              String.format(
                  "Created %d user folders for Lab Admin %s and group %s",
                  createdLabAdminFolders,
                  labAdminWithViewAll.getUsername(),
                  group.getDisplayName()));
          affectedLabAdminCounter++;
          labAdminFoldersCreatedCounter += createdLabAdminFolders;
        }
      }
    }
  }

  private int createMissingUserFolders(User admin, Set<User> nonPiMembers) {

    int createdFoldersCounter = 0;
    Folder adminLabGroupsFolder = folderDao.getLabGroupFolderForUser(admin);

    for (User member : nonPiMembers) {
      if (member.equals(admin) || member.hasRole(Role.PI_ROLE)) {
        continue; // skip yourself, and skip PIs
      }
      Folder groupMemberRoot = folderMgr.getRootFolderForUser(member);
      if (!adminLabGroupsFolder.getChildrens().contains(groupMemberRoot)) {
        folderMgr.addChild(
            adminLabGroupsFolder.getId(), groupMemberRoot, admin, ACLPropagationPolicy.NULL_POLICY);
        createdFoldersCounter++;
      }
    }
    return createdFoldersCounter;
  }

  @Override
  public String getConfirmationMessage() {
    return updateSuccessMessage;
  }
}
