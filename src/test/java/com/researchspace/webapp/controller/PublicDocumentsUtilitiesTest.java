package com.researchspace.webapp.controller;

import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.auth.AnonymousGuestRealm;
import com.researchspace.auth.FirstSuccessOrExceptionAuthStrategy;
import com.researchspace.auth.ShiroRealm;
import com.researchspace.auth.UsernamePasswordCredentialsMatcher;
import com.researchspace.auth.password.SentinelPasswordCheck;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserManager;
import java.util.List;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.pam.ModularRealmAuthenticator;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PublicDocumentsUtilitiesTest {

  private @Mock UserManager userMgr;
  private @Mock IPropertyHolder properties;
  private @Mock UsernamePasswordCredentialsMatcher credentialsMatcher;
  private @Mock SentinelPasswordCheck sentinelCheck;
  private @InjectMocks ShiroRealm passwordRealm;
  private final AnonymousGuestRealm guestRealm = new AnonymousGuestRealm();

  @BeforeEach
  void wireGuestRealm() {
    // not @InjectMocks: it would replace the realm's own credentials matcher with the mock
    ReflectionTestUtils.setField(guestRealm, "userMgr", userMgr);
  }

  @AfterEach
  void unbindSecurityManager() {
    ThreadContext.unbindSubject();
    ThreadContext.unbindSecurityManager();
  }

  @Test
  void signsInTheGuestWithoutAPasswordCheck() {
    when(userMgr.getUserByUsername(RecordGroupSharing.ANONYMOUS_USER))
        .thenReturn(createAnyUser(RecordGroupSharing.ANONYMOUS_USER));
    DefaultSecurityManager securityManager =
        new DefaultSecurityManager(List.of(passwordRealm, guestRealm));
    ((ModularRealmAuthenticator) securityManager.getAuthenticator())
        .setAuthenticationStrategy(new FirstSuccessOrExceptionAuthStrategy());
    ThreadContext.bind(securityManager);

    PublicDocumentsUtilities.loginAnonymousUser();

    assertEquals(RecordGroupSharing.ANONYMOUS_USER, SecurityUtils.getSubject().getPrincipal());
    verifyNoInteractions(credentialsMatcher, sentinelCheck);
  }
}
