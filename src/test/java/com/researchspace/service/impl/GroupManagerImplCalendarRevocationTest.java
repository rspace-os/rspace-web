package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.booking.service.BookingCalendarRevocationCoordinator;
import com.researchspace.dao.GroupDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.service.MessageSourceUtils;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class GroupManagerImplCalendarRevocationTest {

  @Test
  void annualCleanupRegistersBeforeItsFirstGroupQuery() {
    BookingCalendarRevocationCoordinator revocations =
        mock(BookingCalendarRevocationCoordinator.class);
    GroupDao groups = mock(GroupDao.class);
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    Group group = mock(Group.class);
    User activeMember = mock(User.class);
    GroupManagerImpl manager = new GroupManagerImpl();
    ReflectionTestUtils.setField(manager, "calendarRevocations", revocations);
    ReflectionTestUtils.setField(manager, "groupDao", groups);
    ReflectionTestUtils.setField(manager, "messages", messages);
    when(groups.get(5L)).thenReturn(group);
    when(group.getMembers()).thenReturn(Set.of(activeMember));
    when(activeMember.getLastLogin()).thenReturn(new Date());

    assertThrows(
        IllegalStateException.class,
        () -> manager.removeGroupIfNoMemberLoggedInWithinOneYear(5L, activeMember));

    InOrder order = inOrder(revocations, groups);
    order.verify(revocations).affectAll();
    order.verify(groups).get(5L);
  }
}
