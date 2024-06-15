package com.researchspace.service.impl;

import com.researchspace.auth.GlobalInitSysadminAuthenticationToken;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

/** Creates a default set of SampleTemplates if they don't already exist. */
public class SampleTemplateAppInitialiser extends AbstractAppInitializor {

  @Autowired private SampleTemplateInitializer sampleTemplateCreator;
  @Autowired private UserDao userdao;

  /* This property is only be set to 'true' by prod-profile unit test configuration.
   * That's so production-profile sample templates are never created in standard test run,
   * as that would break most run-profile unit tests that expect run-profile templates. */
  @Value("${startup.skip.inventory.template.creation:false}")
  private boolean skipTemplateCreation;

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    if (skipTemplateCreation) {
      log.info("skipping creation of default inventory templates");
      return;
    }

    final Subject subject = getSubject();
    try {
      GlobalInitSysadminAuthenticationToken sysAdminToken =
          new GlobalInitSysadminAuthenticationToken();
      final User sysAdmin = userdao.getUserByUsername(sysAdminToken.getPrincipal().toString());

      subject.login(sysAdminToken);
      Boolean rc =
          performAuthenticatedAction(
              subject,
              new UserAction(
                  admin -> sampleTemplateCreator.createSampleTemplates(admin), sysAdmin, log));
      if (rc.equals(Boolean.FALSE)) {
        log.error("Fatal error creating sample templates");
      }
    } finally {
      subject.logout();
    }
  }

  Subject getSubject() {
    return SecurityUtils.getSubject();
  }
}
