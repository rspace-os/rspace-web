package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.views.UserView;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OperateAsUserLookupTest {

  static class OperateAsUserLookupTSS extends OperateAsUserLookup {
    Subject subject;
    PrincipalCollection pc;

    Subject getSubject() {
      return subject;
    }

    PrincipalCollection getPrincipalCollection() {
      return pc;
    }
  }

  @Mock UserManager userMgr;
  @Mock Subject subject;
  @Mock PrincipalCollection pc;

  @InjectMocks OperateAsUserLookupTSS lookup;

  User mainUser = TestFactory.createAnyUser("mainUser");

  @Test
  public void ifNotRunAsReturnSameUser() {
    when(subject.isRunAs()).thenReturn(false);
    assertEquals(mainUser, lookup.apply(mainUser));
    verifyNoInteractions(userMgr);
  }

  @Test
  public void ifRunAsReturnPreviousPrincipalUser() {
    when(subject.isRunAs()).thenReturn(true);
    UserView previousPrincipal = new UserView(1L, "previousPrincipal", "a@b.com", " full name");
    when(pc.getPrimaryPrincipal()).thenReturn(previousPrincipal.getUniqueName());
    when(userMgr.getUserViewByUsername(previousPrincipal.getUniqueName()))
        .thenReturn(previousPrincipal);
    assertEquals(previousPrincipal, lookup.apply(mainUser));
  }
}
