package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.researchspace.booking.service.BookingCalendarRevocationCoordinator;
import com.researchspace.dao.GroupDao;
import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PublicationPermissionGateTest {
  @Test
  void onlyPiCanUpdateOnlyThePublicationFieldWithoutRegistering() {
    GroupDao groups = mock(GroupDao.class);
    var permissions = mock(IPermissionUtils.class);
    User subject = mock(User.class);
    Group group = mock(Group.class);
    BookingCalendarRevocationCoordinator revocations =
        mock(BookingCalendarRevocationCoordinator.class);
    GroupManagerImpl manager = new GroupManagerImpl();
    ReflectionTestUtils.setField(manager, "calendarRevocations", revocations);
    ReflectionTestUtils.setField(manager, "groupDao", groups);
    ReflectionTestUtils.setField(manager, "permissnUtils", permissions);
    when(groups.getGroupWithCommunities(5L)).thenReturn(group);
    assertFalse(manager.setPublicationAllowed(5L, true, subject));
    verify(groups, never()).setPublicationAllowed(anyLong(), anyBoolean());
    when(subject.hasRoleInGroup(group, RoleInGroup.PI)).thenReturn(true);
    assertFalse(manager.setPublicationAllowed(5L, true, subject));
    verify(groups, never()).setPublicationAllowed(anyLong(), anyBoolean());
    when(permissions.isPermitted(group, PermissionType.WRITE, subject)).thenReturn(true);
    assertTrue(manager.setPublicationAllowed(5L, true, subject));
    verify(groups).setPublicationAllowed(5L, true);
    verifyNoInteractions(revocations);
    verifyNoInteractions(group);
  }
}
