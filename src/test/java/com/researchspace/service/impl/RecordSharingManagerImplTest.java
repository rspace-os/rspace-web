package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.GroupDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.testutils.TestFactory;
import org.apache.shiro.authz.Permission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RecordSharingManagerImplTest { // } extends SpringTransactionalTest {

  private PermissionFactory perFactory = new DefaultPermissionFactory();

  @InjectMocks protected RecordSharingManagerImpl recordSharingManager;

  @Mock RecordGroupSharingDao groupshareRecordDao;
  @Mock IPermissionUtils permissnUtils;
  @Mock FolderDao folderDao;
  @Mock RecordDao recordDao;
  @Mock UserDao userDao;
  @Mock GroupDao grpDao;

  private Long docId01 = 75567l;
  private Long docId02 = 74205l;
  private Long docId03 = 75633l;

  private RecordGroupSharing rgs;
  private User u;
  private BaseRecord record;

  @Before
  public void setUp() throws Exception {
    u = new User();
    u.setId(1701l);
    u.setUsername("Test user");

    record = TestFactory.createAnyRecord(u);
    record.setId(docId01);

    rgs = new RecordGroupSharing();
    rgs.setSharee(u);
    rgs.setShared(record);

    ConstraintBasedPermission cbp =
        perFactory.createIdPermission(PermissionDomain.RECORD, PermissionType.READ, record.getId());

    when(groupshareRecordDao.get(anyLong())).thenReturn(rgs);
    when(permissnUtils.findBy(any(), any(), any(), any())).thenReturn(cbp);
    when(permissnUtils.createFromString("WRITE")).thenReturn(PermissionType.WRITE);
    when(permissnUtils.createFromString("READ")).thenReturn(PermissionType.READ);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testUserSingleSharedDocChangeFromReadToWrite() {
    u.addPermission("RECORD:READ:id=" + docId01);
    ErrorList el = recordSharingManager.updatePermissionForRecord(1l, "WRITE", "unused");

    assertEquals(el, null);
    assertEquals(u.getPermissions().size(), 1);
    ConstraintBasedPermission finalPermission =
        (ConstraintBasedPermission) u.getPermissions().iterator().next();
    assertEquals(finalPermission.getActions().size(), 1);
    assertEquals(finalPermission.getActions().iterator().next(), PermissionType.WRITE);
    assertEquals(finalPermission.getIdConstraint().getId().iterator().next(), record.getId());

    assertEquals(rgs.getShared().getSharingACL().getAclElements().size(), 1);
    assertEquals(
        rgs.getShared().getSharingACL().getAclElements().get(0).getAsString(),
        "Test user=RECORD:WRITE:");
  }

  @Test
  public void testUserSingleSharedDocChangeFromReadToRead() {
    u.addPermission("RECORD:READ:id=" + docId01);
    ErrorList el = recordSharingManager.updatePermissionForRecord(1l, "READ", "unused");

    assertEquals(el, null);
    assertEquals(u.getPermissions().size(), 1);
    ConstraintBasedPermission finalPermission =
        (ConstraintBasedPermission) u.getPermissions().iterator().next();
    assertEquals(finalPermission.getActions().size(), 1);
    assertEquals(finalPermission.getActions().iterator().next(), PermissionType.READ);
    assertEquals(finalPermission.getIdConstraint().getId().iterator().next(), record.getId());

    assertEquals(rgs.getShared().getSharingACL().getAclElements().size(), 1);
    assertEquals(
        rgs.getShared().getSharingACL().getAclElements().get(0).getAsString(),
        "Test user=RECORD:READ:");
  }

  @Test
  public void testUserSingleSharedDocChangeFromEditToRead() {
    u.addPermission("RECORD:WRITE:id=" + docId01);
    ErrorList el = recordSharingManager.updatePermissionForRecord(1l, "READ", "unused");

    assertEquals(el, null);
    assertEquals(u.getPermissions().size(), 1);
    ConstraintBasedPermission finalPermission =
        (ConstraintBasedPermission) u.getPermissions().iterator().next();
    assertEquals(finalPermission.getActions().size(), 1);
    assertEquals(finalPermission.getActions().iterator().next(), PermissionType.READ);
    assertEquals(finalPermission.getIdConstraint().getId().iterator().next(), record.getId());

    assertEquals(rgs.getShared().getSharingACL().getAclElements().size(), 1);
    assertEquals(
        rgs.getShared().getSharingACL().getAclElements().get(0).getAsString(),
        "Test user=RECORD:READ:");
  }

  @Test
  public void testUserSingleSharedDocChangeFromEditToEdit() {
    u.addPermission("RECORD:WRITE:id=" + docId01);
    ErrorList el = recordSharingManager.updatePermissionForRecord(1l, "WRITE", "unused");

    assertEquals(el, null);
    assertEquals(u.getPermissions().size(), 1);
    ConstraintBasedPermission finalPermission =
        (ConstraintBasedPermission) u.getPermissions().iterator().next();
    assertEquals(finalPermission.getActions().size(), 1);
    assertEquals(finalPermission.getActions().iterator().next(), PermissionType.WRITE);
    assertEquals(finalPermission.getIdConstraint().getId().iterator().next(), record.getId());

    assertEquals(rgs.getShared().getSharingACL().getAclElements().size(), 1);
    assertEquals(
        rgs.getShared().getSharingACL().getAclElements().get(0).getAsString(),
        "Test user=RECORD:WRITE:");
  }

  @Test
  public void testUserTwoSharedDocsChangeOneFromReadToWrite() {
    u.addPermission("RECORD:READ:id=" + docId01);
    u.addPermission("RECORD:READ:id=" + docId02);
    ErrorList el = recordSharingManager.updatePermissionForRecord(1l, "WRITE", "unused");

    assertEquals(el, null);
    assertEquals(u.getPermissions().size(), 2);

    boolean readFound = false;
    boolean writeFound = false;

    for (Permission p : u.getPermissions()) {
      ConstraintBasedPermission cbp = (ConstraintBasedPermission) p;
      if (cbp.getActions().iterator().next().equals(PermissionType.WRITE)) {
        writeFound = true;
        assertEquals(cbp.getIdConstraint().getId().iterator().next(), docId01);
      } else if (cbp.getActions().iterator().next().equals(PermissionType.READ)) {
        readFound = true;
        assertEquals(cbp.getIdConstraint().getId().iterator().next(), docId02);
      }
    }

    assertEquals(readFound, true);
    assertEquals(writeFound, true);

    assertEquals(rgs.getShared().getSharingACL().getAclElements().size(), 1);
    assertEquals(
        rgs.getShared().getSharingACL().getAclElements().get(0).getAsString(),
        "Test user=RECORD:WRITE:");
  }

  @Test
  public void testUserTwoSharedDocsChangeOneFromWriteToRead() {
    u.addPermission("RECORD:WRITE:id=" + docId01);
    u.addPermission("RECORD:READ:id=" + docId02);
    ErrorList el = recordSharingManager.updatePermissionForRecord(1l, "READ", "unused");

    assertEquals(el, null);
    assertEquals(u.getPermissions().size(), 2);

    boolean readFound = false;
    boolean writeFound = false;
    boolean doc1Found = false;
    boolean doc2Found = false;

    for (Permission p : u.getPermissions()) {
      ConstraintBasedPermission cbp = (ConstraintBasedPermission) p;
      if (cbp.getActions().iterator().next().equals(PermissionType.WRITE)) {
        writeFound = true;
      } else if (cbp.getActions().iterator().next().equals(PermissionType.READ)) {
        readFound = true;
        if (cbp.getIdConstraint().getId().iterator().next().equals(docId01)) {
          doc1Found = true;
        } else if (cbp.getIdConstraint().getId().iterator().next().equals(docId02)) {
          doc2Found = true;
        }
      }
    }

    assertEquals(readFound, true);
    assertEquals(writeFound, false);
    assertEquals(doc1Found, true);
    assertEquals(doc2Found, true);

    assertEquals(rgs.getShared().getSharingACL().getAclElements().size(), 1);
    assertEquals(
        rgs.getShared().getSharingACL().getAclElements().get(0).getAsString(),
        "Test user=RECORD:READ:");
  }

  // The following four tests ("test...TwoIDsInOnePerm") test for the issue
  // in which if a permission existed on a user in the form, e.g, "READ:id=12,34"
  // then the permissions would not update correctly.
  @Test
  public void testUserTwoSharedDocsChangeOneFromReadToWriteTwoIDsInOnePerm() {
    u.addPermission("RECORD:READ:id=" + docId02 + "," + docId01);
    ErrorList el = recordSharingManager.updatePermissionForRecord(1l, "WRITE", "unused");

    assertEquals(el, null);
    assertEquals(u.getPermissions().size(), 2);

    boolean readFound = false;
    boolean writeFound = false;

    for (Permission p : u.getPermissions()) {
      ConstraintBasedPermission cbp = (ConstraintBasedPermission) p;
      if (cbp.getActions().iterator().next().equals(PermissionType.WRITE)) {
        writeFound = true;
        assertEquals(cbp.getIdConstraint().getId().iterator().next(), docId01);
      } else if (cbp.getActions().iterator().next().equals(PermissionType.READ)) {
        readFound = true;
        assertEquals(cbp.getIdConstraint().getId().iterator().next(), docId02);
      }
    }

    assertEquals(readFound, true);
    assertEquals(writeFound, true);

    assertEquals(rgs.getShared().getSharingACL().getAclElements().size(), 1);
    assertEquals(
        rgs.getShared().getSharingACL().getAclElements().get(0).getAsString(),
        "Test user=RECORD:WRITE:");
  }

  @Test
  public void testUserTwoSharedDocsChangeOneFromReadToReadTwoIDsInOnePerm() {
    u.addPermission("RECORD:READ:id=" + docId02 + "," + docId01);
    ErrorList el = recordSharingManager.updatePermissionForRecord(1l, "READ", "unused");

    assertEquals(el, null);
    assertEquals(u.getPermissions().size(), 2);

    boolean readFound = false;
    boolean writeFound = false;
    boolean doc1Found = false;
    boolean doc2Found = false;

    for (Permission p : u.getPermissions()) {
      ConstraintBasedPermission cbp = (ConstraintBasedPermission) p;
      if (cbp.getActions().iterator().next().equals(PermissionType.WRITE)) {
        writeFound = true;
      } else if (cbp.getActions().iterator().next().equals(PermissionType.READ)) {
        readFound = true;
        if (cbp.getIdConstraint().getId().iterator().next().equals(docId01)) {
          doc1Found = true;
        } else if (cbp.getIdConstraint().getId().iterator().next().equals(docId02)) {
          doc2Found = true;
        }
      }
    }

    assertEquals(readFound, true);
    assertEquals(writeFound, false);
    assertEquals(doc1Found, true);
    assertEquals(doc2Found, true);

    assertEquals(rgs.getShared().getSharingACL().getAclElements().size(), 1);
    assertEquals(
        rgs.getShared().getSharingACL().getAclElements().get(0).getAsString(),
        "Test user=RECORD:READ:");
  }

  @Test
  public void testUserTwoSharedDocsChangeOneFromWriteToReadTwoIDsInOnePerm() {
    u.addPermission("RECORD:WRITE:id=" + docId02 + "," + docId01);
    ErrorList el = recordSharingManager.updatePermissionForRecord(1l, "READ", "unused");

    assertEquals(el, null);
    assertEquals(u.getPermissions().size(), 2);

    boolean readFound = false;
    boolean writeFound = false;

    for (Permission p : u.getPermissions()) {
      ConstraintBasedPermission cbp = (ConstraintBasedPermission) p;
      if (cbp.getActions().iterator().next().equals(PermissionType.WRITE)) {
        writeFound = true;
        assertEquals(cbp.getIdConstraint().getId().iterator().next(), docId02);
      } else if (cbp.getActions().iterator().next().equals(PermissionType.READ)) {
        readFound = true;
        assertEquals(cbp.getIdConstraint().getId().iterator().next(), docId01);
      }
    }

    assertEquals(readFound, true);
    assertEquals(writeFound, true);

    assertEquals(rgs.getShared().getSharingACL().getAclElements().size(), 1);
    assertEquals(
        rgs.getShared().getSharingACL().getAclElements().get(0).getAsString(),
        "Test user=RECORD:READ:");
  }

  @Test
  public void testUserTwoSharedDocsChangeOneFromWriteToWriteTwoIDsInOnePerm() {
    u.addPermission("RECORD:WRITE:id=" + docId02 + "," + docId01);
    ErrorList el = recordSharingManager.updatePermissionForRecord(1l, "WRITE", "unused");

    assertEquals(el, null);
    assertEquals(u.getPermissions().size(), 2);

    boolean readFound = false;
    boolean writeFound = false;
    boolean doc1Found = false;
    boolean doc2Found = false;

    for (Permission p : u.getPermissions()) {
      ConstraintBasedPermission cbp = (ConstraintBasedPermission) p;
      if (cbp.getActions().iterator().next().equals(PermissionType.READ)) {
        readFound = true;
      } else if (cbp.getActions().iterator().next().equals(PermissionType.WRITE)) {
        writeFound = true;
        if (cbp.getIdConstraint().getId().iterator().next().equals(docId01)) {
          doc1Found = true;
        } else if (cbp.getIdConstraint().getId().iterator().next().equals(docId02)) {
          doc2Found = true;
        }
      }
    }

    assertEquals(readFound, false);
    assertEquals(writeFound, true);
    assertEquals(doc1Found, true);
    assertEquals(doc2Found, true);

    assertEquals(rgs.getShared().getSharingACL().getAclElements().size(), 1);
    assertEquals(
        rgs.getShared().getSharingACL().getAclElements().get(0).getAsString(),
        "Test user=RECORD:WRITE:");
  }

  // Test that when we start with multiple permissions, as when
  // coming from an RSpace running with the existing bug, that
  // we can correctly clear up the permissions
  @Test
  public void testUserThreeSharedDocsChangeOneFromReadToWrite() {
    u.addPermission("RECORD:WRITE:id=" + docId01);
    u.addPermission("RECORD:WRITE:id=" + docId01);
    u.addPermission("RECORD:WRITE:id=" + docId01);
    u.addPermission("RECORD:WRITE:id=" + docId01);
    u.addPermission("RECORD:WRITE:id=" + docId02);
    u.addPermission("RECORD:WRITE:id=" + docId03);
    u.addPermission("RECORD:READ:id=" + docId01);
    u.addPermission("RECORD:READ:id=" + docId02);
    u.addPermission("RECORD:READ:id=" + docId03);
    ErrorList el = recordSharingManager.updatePermissionForRecord(1l, "READ", "unused");

    assertEquals(el, null);
    assertEquals(u.getPermissions().size(), 5);

    boolean readFound = false;
    boolean writeFound = false;
    boolean doc1FoundRead = false;
    boolean doc2FoundRead = false;
    boolean doc3FoundRead = false;
    boolean doc1FoundWrite = false;
    boolean doc2FoundWrite = false;
    boolean doc3FoundWrite = false;

    for (Permission p : u.getPermissions()) {
      ConstraintBasedPermission cbp = (ConstraintBasedPermission) p;
      if (cbp.getActions().iterator().next().equals(PermissionType.WRITE)) {
        writeFound = true;
        if (cbp.getIdConstraint().getId().iterator().next().equals(docId01)) {
          doc1FoundWrite = true;
        } else if (cbp.getIdConstraint().getId().iterator().next().equals(docId02)) {
          doc2FoundWrite = true;
        } else if (cbp.getIdConstraint().getId().iterator().next().equals(docId03)) {
          doc3FoundWrite = true;
        }
      } else if (cbp.getActions().iterator().next().equals(PermissionType.READ)) {
        readFound = true;
        if (cbp.getIdConstraint().getId().iterator().next().equals(docId01)) {
          doc1FoundRead = true;
        } else if (cbp.getIdConstraint().getId().iterator().next().equals(docId02)) {
          doc2FoundRead = true;
        } else if (cbp.getIdConstraint().getId().iterator().next().equals(docId03)) {
          doc3FoundRead = true;
        }
      }
    }

    assertEquals(readFound, true);
    assertEquals(writeFound, true);
    assertEquals(doc1FoundRead, true);
    assertEquals(doc2FoundRead, true);
    assertEquals(doc3FoundRead, true);
    assertEquals(doc1FoundWrite, false);
    assertEquals(doc2FoundWrite, true);
    assertEquals(doc3FoundWrite, true);
  }
}
