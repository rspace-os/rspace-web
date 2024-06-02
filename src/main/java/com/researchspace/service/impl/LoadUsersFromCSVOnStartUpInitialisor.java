package com.researchspace.service.impl;

import com.axiope.userimport.UserImportResult;
import com.axiope.userimport.UserListGenerator;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import com.researchspace.model.dto.GroupPublicInfo;
import com.researchspace.model.dto.UserRegistrationInfo;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.UserExistsException;
import java.io.IOException;
import java.io.InputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component("loadUsersFromCSVOnStartUpInitialisor")
@Deprecated // this is not used in production and doesn't create a useful ser.
//  admin users to bootstrap system are now created through liquibase
public class LoadUsersFromCSVOnStartUpInitialisor extends AbstractAppInitializor {

  private Logger logger = LogManager.getLogger(LoadUsersFromCSVOnStartUpInitialisor.class);

  private @Autowired UserListGenerator importer;
  private @Autowired ResourceLoader resourceLoader;

  private Resource getResource(String location) {
    return resourceLoader.getResource(location);
  }

  private IContentInitializer contentInitializor;

  @Autowired
  public void setContentInitializor(IContentInitializer contentInitializor) {
    this.contentInitializor = contentInitializor;
  }

  // if not null, should be location on classpath of a CSV file of users and possibly groups
  @Value("${rs.usergroup.initfile}")
  private String loadUsersOnStartUpFile;

  /*
   * For testing
   */
  public void setLoadUsersOnStartUpFile(String loadUsersOnStartUpFile) {
    this.loadUsersOnStartUpFile = loadUsersOnStartUpFile;
  }

  public String getLoadUsersOnStartUpFile() {
    return loadUsersOnStartUpFile;
  }

  @Override
  public void onInitialAppDeployment() {
    if (loadUsersOnStartUpFile == null) {
      return;
    }
    Subject subject = SecurityUtils.getSubject();
    try (InputStream is = getResource("classpath:" + loadUsersOnStartUpFile).getInputStream()) {
      if (is == null) {
        logger.error(
            "Could not obtain input stream for resource :[" + loadUsersOnStartUpFile + "]");
        return;
      }
      final UserImportResult parsed = importer.getUsersToSignup(is);
      if (parsed.hasErrors()) {
        logger.error("Could not initialise system with resource [" + loadUsersOnStartUpFile + "]");
        return;
      }

      for (UserRegistrationInfo userRegInfo : parsed.getParsedUsers()) {
        User user = userRegInfo.toUser();
        String origpwd = user.getPassword();

        try {
          userMgr.saveNewUser(user);
          UsernamePasswordToken token =
              new UsernamePasswordToken(user.getUsername(), origpwd, false);

          login(token);
          performAuthenticatedAction(
              subject, new UserIdAction(userId -> contentInitializor.init(userId), user, logger));

        } catch (UserExistsException e) {
          logger.warn("User  [" + user.getUsername() + "] already exists, skipping");
        } finally {
          logout();
        }
      }

      login(new UsernamePasswordToken(ADMIN_UNAME, ADMIN_PWD, false));
      final User admin = userMgr.getUserByUsername(ADMIN_UNAME);
      subject.execute(
          () -> {
            if (!admin.isContentInitialized()) {
              contentInitializor.init(admin.getId());
            }
            for (GroupPublicInfo grpInfo : parsed.getParsedGroups()) {
              Group group = grpInfo.toGroup();
              User[] allUsersInGroup = parsed.getUsersFromMemberString(group.getMemberString());
              grpStrategy.createAndSaveGroup(
                  group.getDisplayName(),
                  parsed.getUserFromUsername(group.getPis()),
                  admin,
                  GroupType.LAB_GROUP,
                  allUsersInGroup);
            }
            return true;
          });
      logout();

    } catch (IOException e) {
      logger.error("Problem during app initialisation.", e);
    }
  }
}
