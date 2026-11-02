package com.researchspace.auth;

import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnonymousGuestRealmTest {

  private @Mock UserManager userMgr;
  private @InjectMocks AnonymousGuestRealm realm;

  @Test
  void signsInTheGuestAsAPlainUsername() {
    when(userMgr.getUserByUsername(RecordGroupSharing.ANONYMOUS_USER))
        .thenReturn(createAnyUser(RecordGroupSharing.ANONYMOUS_USER));

    AuthenticationInfo info = realm.getAuthenticationInfo(new AnonymousGuestAuthenticationToken());

    assertEquals(
        RecordGroupSharing.ANONYMOUS_USER,
        info.getPrincipals().fromRealm(realm.getName()).iterator().next());
  }

  @Test
  void refusesTheGuestWhenItsAccountIsDisabled() {
    User guest = createAnyUser(RecordGroupSharing.ANONYMOUS_USER);
    guest.setEnabled(false);
    when(userMgr.getUserByUsername(RecordGroupSharing.ANONYMOUS_USER)).thenReturn(guest);

    assertThrows(
        DisabledAccountException.class,
        () -> realm.getAuthenticationInfo(new AnonymousGuestAuthenticationToken()));
  }

  @Test
  void doesNotAcceptAPasswordLogin() {
    assertFalse(
        realm.supports(new UsernamePasswordToken(RecordGroupSharing.ANONYMOUS_USER, "anything")));
  }
}
