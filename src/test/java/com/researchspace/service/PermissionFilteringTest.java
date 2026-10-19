package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.Constants;
import com.researchspace.auth.PermissionUtils;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.impl.ShiroTestUtils;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.List;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionFilteringTest {
  @Mock Subject subject;
  private final ShiroTestUtils shiroUtils = new ShiroTestUtils();

  @BeforeEach
  void setUp() {
    shiroUtils.setSubject(subject);
  }

  @AfterEach
  void tearDown() {
    shiroUtils.clearSubject();
  }

  @Test
  void renameFilteringRetainsOnlyOwnedRecords() throws Exception {
    User owner = TestFactory.createAnyUserWithRole("owner", Constants.USER_ROLE);
    User other = TestFactory.createAnyUserWithRole("other", Constants.USER_ROLE);
    RSForm form = TestFactory.createAnyForm();
    List<BaseRecord> records = new ArrayList<>();
    long id = 1;
    for (User user : List.of(owner, other)) {
      Folder root = TestFactory.createAFolder(user.getUsername(), user);
      root.setId(id++);
      root.setType(RecordType.ROOT.name());
      new DefaultPermissionFactory().setUpACLForUserRoot(user, root);
      for (int i = 0; i < 2; i++) {
        BaseRecord record = TestFactory.createAnySDForUser(form, user);
        record.setId(id++);
        root.addChild(record, user);
        records.add(record);
      }
    }

    new PermissionUtils().filter(records, PermissionType.RENAME, owner);

    assertEquals(List.of(2L, 3L), records.stream().map(BaseRecord::getId).toList());
  }
}
