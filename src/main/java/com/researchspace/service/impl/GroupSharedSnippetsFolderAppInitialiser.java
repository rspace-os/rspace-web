package com.researchspace.service.impl;

import com.researchspace.auth.GlobalInitSysadminAuthenticationToken;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.service.GroupManager;
import com.researchspace.service.UserManager;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * If not already created, creates the shared snippets folder for pre-existing groups on app
 * startup. Note that for fresh RSpace installs, the shared snippets folder will be created by the
 * initial startup AbstractContentInitializer init method
 */
public class GroupSharedSnippetsFolderAppInitialiser extends AbstractAppInitializor {

  @Autowired private GroupManager groupManager;
  @Autowired private UserManager userManager;

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {

    final Subject subject = getSubject();
    try {
      GlobalInitSysadminAuthenticationToken sysAdminToken =
          new GlobalInitSysadminAuthenticationToken();
      final User sysAdmin = userManager.getUserByUsername(sysAdminToken.getPrincipal().toString());

      subject.login(sysAdminToken);
      for (Group group : groupManager.list()) {
        Boolean rc =
            performAuthenticatedAction(
                subject,
                new UserAction(
                    admin ->
                        groupManager.createSharedCommunalGroupFolders(
                            group.getId(), sysAdmin.getUsername()),
                    sysAdmin,
                    log));
        if (rc.equals(Boolean.FALSE)) {
          log.error("Fatal error creating group shared snippets folder");
        }
      }
    } finally {
      subject.logout();
    }
  }

  Subject getSubject() {
    return SecurityUtils.getSubject();
  }
}
