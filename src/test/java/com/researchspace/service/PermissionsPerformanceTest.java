package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.auth.PermissionUtils;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.impl.ConditionalTestRunnerNotSpring;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.service.impl.ShiroTestUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.time.StopWatch;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(ConditionalTestRunnerNotSpring.class)
public class PermissionsPerformanceTest {
  private static final int NUM_RECORDS_TO_CREATE = 500;
  PermissionFactory permFac;
  IPermissionUtils utils;
  ShiroTestUtils shiroUtils;
  @Mock Subject subject;
  public @Rule MockitoRule rule = MockitoJUnit.rule();

  Map<String, User> map = new HashMap<>();
  ShiroTestUtils testUtils;

  @Before
  public void setUp() throws Exception {
    permFac = new DefaultPermissionFactory();
    utils = new PermissionUtils();
    shiroUtils = new ShiroTestUtils();
    shiroUtils.setSubject(subject);
  }

  @After
  public void tearDown() throws Exception {
    shiroUtils.clearSubject();
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void test() throws IllegalAddChildOperation, InterruptedException {
    RSForm form = TestFactory.createAnyForm();
    StopWatch sw = new StopWatch();
    sw.start();
    User u = createUser("user");
    Folder root = createNRecords(NUM_RECORDS_TO_CREATE, u, form);
    Set<BaseRecord> toFilter = root.getChildrens();
    // set up 20 users with permissions and 500 records each
    for (int i = 0; i < 20; i++) {
      User other = createUser("userx" + i);
      Folder otherRoot = createNRecords(NUM_RECORDS_TO_CREATE, other, form);
      toFilter.addAll(otherRoot.getChildrens());
    }

    sw.stop();
    System.err.println("generating records  took: " + sw.getTime() + " ms");
    sw.reset();

    SecurityUtils.getSubject().login(new UsernamePasswordToken(u.getUsername(), u.getPassword()));

    System.err.println("filtering " + toFilter.size() + " records.");
    sw.start();
    // this will go through subject permissions and ACLS
    utils.filter(toFilter, PermissionType.RENAME, u);
    sw.split();
    System.err.println("Filtering took: " + sw.toSplitString());
    assertTrue(
        "Should be less than 1500ms but was " + sw.getSplitTime(),
        sw.getSplitTime() < 1500); // check that 10000 records->500 takes < 1.5 second.
    assertEquals(NUM_RECORDS_TO_CREATE, toFilter.size());
  }

  private User createUser(String uname) {
    User u = TestFactory.createAnyUserWithRole(uname, Constants.USER_ROLE);

    map.put(u.getUsername(), u);
    return u;
  }

  private Folder createNRecords(int numRecords, User u, RSForm form)
      throws IllegalAddChildOperation, InterruptedException {
    List<BaseRecord> rc = new ArrayList<BaseRecord>();
    Folder root = TestFactory.createAFolder("root", u);
    root.setType(RecordType.ROOT.name());
    permFac.setUpACLForUserRoot(u, root);
    for (int i = 0; i < numRecords; i++) {
      BaseRecord br = TestFactory.createAnySDForUser(form, u);
      root.addChild(br, u);
      Thread.sleep(1);
    }
    return root;
  }
}
