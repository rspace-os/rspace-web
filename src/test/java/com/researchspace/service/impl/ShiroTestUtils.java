package com.researchspace.service.impl;

import static com.researchspace.testutils.BaseManagerTestCaseBase.TESTPASSWD;

import com.researchspace.model.User;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.SubjectThreadState;
import org.apache.shiro.util.LifecycleUtils;
import org.apache.shiro.util.ThreadState;
import org.junit.AfterClass;

/** Test case enabling Shiro config in test environments. */
public class ShiroTestUtils {

  private static ThreadState subjectThreadState;

  public ShiroTestUtils() {}

  /**
   * Allows subclasses to set the currently executing {@link Subject} instance.
   *
   * @param subject the Subject instance
   */
  public void setSubject(Subject subject) {
    clearSubject();
    subjectThreadState = createThreadState(subject);
    subjectThreadState.bind();
  }

  protected Subject getSubject() {
    return SecurityUtils.getSubject();
  }

  protected ThreadState createThreadState(Subject subject) {
    return new SubjectThreadState(subject);
  }

  /** Clears Shiro's thread state, ensuring the thread remains clean for future test execution. */
  public void clearSubject() {
    doClearSubject();
  }

  private static void doClearSubject() {
    if (subjectThreadState != null) {
      subjectThreadState.clear();
      subjectThreadState = null;
    }
  }

  protected static SecurityManager getSecurityManager() {
    return SecurityUtils.getSecurityManager();
  }

  @AfterClass
  public static void tearDownShiro() {
    doClearSubject();
    try {
      SecurityManager securityManager = getSecurityManager();
      LifecycleUtils.destroy(securityManager);
    } catch (UnavailableSecurityManagerException e) {
      // we don't care about this when cleaning up the test environment
      // (for example, maybe the subclass is a unit test and it didn't
      // need a SecurityManager instance because it was using only
      // mock Subject instances)
    }
  }

  public Subject doLogin(User user) {
    Subject subjectUnderTest =
        new Subject.Builder(SecurityUtils.getSecurityManager()).buildSubject();
    subjectUnderTest.login(new UsernamePasswordToken(user.getUsername(), TESTPASSWD));
    setSubject(subjectUnderTest);
    return subjectUnderTest;
  }
}
