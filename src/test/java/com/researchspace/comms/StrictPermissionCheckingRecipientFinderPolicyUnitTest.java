package com.researchspace.comms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.testutils.TestFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RSDEV-992: a blank search term must not trigger {@link UserDao#searchUsers(String)}, which runs
 * an unindexed ILIKE '%term%' over the whole users table; the intersection it feeds keeps every
 * target anyway. The mentions UI sends term="" on the initial '@' keypress, so on large instances
 * the scan made the recipients lookup slow enough to time out and show an empty mentions list.
 */
@ExtendWith(MockitoExtension.class)
class StrictPermissionCheckingRecipientFinderPolicyUnitTest {

  @Mock private UserDao userDao;
  @Mock private RecordGroupSharingDao groupShareDao;

  @InjectMocks private StrictPermissionCheckingRecipientFinderPolicy policy;

  private User pi;
  private User member;
  private User subject;

  @BeforeEach
  void setUp() {
    pi = TestFactory.createAnyUserWithRole("pi992", Role.PI_ROLE.getName());
    member = TestFactory.createAnyUser("member992");
    subject = TestFactory.createAnyUser("subject992");
    TestFactory.createAnyGroup(pi, member, subject);
  }

  @Test
  void blankSearchTermDoesNotScanUserTableAndBehavesLikeNullTerm() {
    Set<User> nullTermTargets =
        policy.findPotentialTargetsFor(MessageType.SIMPLE_MESSAGE, null, null, subject);
    Set<User> emptyTermTargets =
        policy.findPotentialTargetsFor(MessageType.SIMPLE_MESSAGE, null, "", subject);
    Set<User> whitespaceTermTargets =
        policy.findPotentialTargetsFor(MessageType.SIMPLE_MESSAGE, null, "   ", subject);

    verify(userDao, never()).searchUsers(anyString());
    assertEquals(nullTermTargets, emptyTermTargets);
    assertEquals(nullTermTargets, whitespaceTermTargets);
    assertTrue(emptyTermTargets.containsAll(new HashSet<>(Arrays.asList(pi, member))));
  }

  @Test
  void nonBlankSearchTermStillFiltersTargets() {
    when(userDao.searchUsers("memb")).thenReturn(Collections.singletonList(member));

    Set<User> targets =
        policy.findPotentialTargetsFor(MessageType.SIMPLE_MESSAGE, null, "memb", subject);

    verify(userDao).searchUsers("memb");
    assertEquals(Collections.singleton(member), targets);
  }
}
