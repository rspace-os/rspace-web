package com.researchspace.service.impl;

import static com.researchspace.testutils.SystemPropertyTestFactory.createPermissionEnumSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.dao.SystemPropertyDao;
import com.researchspace.model.Community;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.testutils.TestFactory;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SystemPropertyManagerImplTest {
  @Mock private SystemPropertyDao dao;
  @Mock private IPermissionUtils permissionUtils;
  private SystemPropertyManagerImpl manager;
  private Community community;
  private SystemPropertyValue value;

  @BeforeEach
  void setUp() {
    manager = new SystemPropertyManagerImpl();
    manager.setSystemPropertyDao(dao);
    ReflectionTestUtils.setField(manager, "permissionUtils", permissionUtils);
    community = new Community();
    community.setId(10L);
    value =
        new SystemPropertyValue(
            createPermissionEnumSystemProperty(), HierarchicalPermission.ALLOWED.name(), community);
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void communityWritesRequirePermissionEvenForAdmins(boolean byId, boolean admin) {
    User subject = TestFactory.createAnyUser("unauthorized");
    if (admin) {
      subject.addRole(Role.ADMIN_ROLE);
    }
    if (byId) {
      when(dao.get(42L)).thenReturn(value);
    }

    assertThrows(AuthorizationException.class, () -> save(byId, subject));

    verify(permissionUtils).isPermitted(community, PermissionType.WRITE, subject);
    verify(dao, never()).save(any());
    assertEquals(HierarchicalPermission.ALLOWED.name(), value.getValue());
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void communityWritesWithPermissionAreSaved(boolean byId, boolean sysadmin) {
    User subject = TestFactory.createAnyUser("authorized");
    subject.addRole(sysadmin ? Role.SYSTEM_ROLE : Role.ADMIN_ROLE);
    if (byId) {
      when(dao.get(42L)).thenReturn(value);
    }
    when(permissionUtils.isPermitted(community, PermissionType.WRITE, subject)).thenReturn(true);
    when(dao.save(value)).thenReturn(value);

    assertSame(value, save(byId, subject));

    verify(dao).save(value);
  }

  @Test
  void nullSubjectDoesNotBypassCommunityAuthorization() {
    assertThrows(AuthorizationException.class, () -> manager.save(value, null));

    verify(permissionUtils).isPermitted(community, PermissionType.WRITE, null);
    verify(dao, never()).save(any());
  }

  @Test
  void globalSettingsStillRequireSysadminOrSystem() {
    value.setCommunity(null);
    User subject = TestFactory.createAnyUser("global");
    assertThrows(AuthorizationException.class, () -> manager.save(value, subject));
    subject.addRole(Role.ADMIN_ROLE);
    assertThrows(AuthorizationException.class, () -> manager.save(value, subject));

    subject.addRole(Role.SYSTEM_ROLE);
    when(dao.save(value)).thenReturn(value);
    assertSame(value, manager.save(value, subject));
    assertSame(value, manager.save(value, null));
    verifyNoInteractions(permissionUtils);
  }

  private SystemPropertyValue save(boolean byId, User subject) {
    return byId
        ? manager.save(42L, HierarchicalPermission.DENIED.name(), subject)
        : manager.save(value, subject);
  }
}
