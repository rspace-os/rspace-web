package com.researchspace.service.impl;

import com.researchspace.dao.UserDao;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import com.researchspace.service.IContentInitializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class DeveloperGroupSetup extends AbstractAppInitializor {

  @Value("${default.user.password}")
  private String devUserPassword;

  Logger logger = LogManager.getLogger(DeveloperGroupSetup.class);
  private UserDao userdao;

  @Autowired
  public void setUserdao(UserDao userdao) {
    this.userdao = userdao;
  }

  // if not null, will create developer groups
  @Value("${rs.dev.groupcreation}")
  private String createTestGroups;

  private IContentInitializer contentInitializor;

  @Autowired
  public void setContentInitializor(IContentInitializer contentInitializor) {
    this.contentInitializor = contentInitializor;
  }

  @Override
  public void onInitialAppDeployment() {
    if (!"true".equals(createTestGroups)) {
      return;
    }

    UsernamePasswordToken admintoken =
        new UsernamePasswordToken(
            AbstractAppInitializor.SYSADMIN_UNAME, AbstractAppInitializor.SYSADMIN_PWD, false);
    final User admin = userdao.getUserByUsername(admintoken.getUsername());

    String[] users = new String[] {"user1a", "user2b", "user3c", "user4d"};
    List<UsernamePasswordToken> tokesn = new ArrayList<>();
    for (String uname : users) {
      UsernamePasswordToken token2 = new UsernamePasswordToken(uname, devUserPassword, false);
      tokesn.add(token2);
    }
    Map<String, User> uname2user = new HashMap<>();
    final Subject subject = SecurityUtils.getSubject();
    for (UsernamePasswordToken tok : tokesn) {
      final User user = userdao.getUserByUsername(tok.getUsername());
      uname2user.put(user.getUsername(), user);
      subject.login(tok);
      Boolean rc =
          performAuthenticatedAction(
              subject, new UserIdAction(userId -> contentInitializor.init(userId), user, logger));
      if (rc.equals(Boolean.FALSE)) {
        logger.error("Fatal error creating dev groups");
      }
      subject.logout();
    }
    subject.login(admintoken);

    try {
      grpStrategy.createAndSaveGroup(
          uname2user.get("user1a"),
          admin,
          GroupType.LAB_GROUP,
          uname2user.get("user1a"),
          uname2user.get("user2b"));
      grpStrategy.createAndSaveGroup(
          uname2user.get("user3c"),
          admin,
          GroupType.LAB_GROUP,
          uname2user.get("user3c"),
          uname2user.get("user4d"));
    } finally {
      subject.logout();
    }
  }
}
